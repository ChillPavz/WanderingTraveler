package com.chillpavz.wanderingtraveler.title;

import com.chillpavz.wanderingtraveler.Constants;
import com.chillpavz.wanderingtraveler.config.TravelerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Watches the biome under the player and decides when a title is worth showing.
 *
 * <p>Title spam is the single loudest gameplay complaint on the incumbent - five open or
 * closed issues plus the same request on the other mod - so the suppression rules are
 * here from v1 rather than bolted on. Pacing (never stamping one title over another) is
 * separate and lives in {@link TitleState}; this class only decides WHETHER.
 */
public final class TitleTracker {

    /**
     * How far below ground level counts as underground, in blocks.
     *
     * <p>Big enough that a house, a bridge or an overhang does not read as a cave, small
     * enough that the top of a real cave does.
     */
    private static final int UNDERGROUND_DEPTH = 8;

    /**
     * The columns sampled when asking whether a biome reaches the surface, as block
     * offsets on each axis. Seven values each way is 49 columns spanning 96 blocks.
     *
     * <p>SAMPLING WIDER IS ONLY EVER SAFER. A cave biome never appears at the surface at
     * any distance, so widening cannot produce a false hide; all it does is give a
     * surface biome more chances to be recognised as one.
     */
    private static final int[] SAMPLE_OFFSETS = {0, 16, -16, 32, -32, 48, -48};

    /** Titles already shown, most recent last. Compared by DISPLAYED NAME, not by id. */
    private static final Deque<String> recent = new ArrayDeque<>();
    private static final Set<String> everShown = new HashSet<>();

    private static Identifier lastBiome;
    private static Identifier lastDimension;
    private static boolean joinedThisSession;

    /**
     * Was {@link #lastBiome} hidden by the underground rule rather than shown?
     *
     * <p>A biome the player never saw a title for is NOT "already handled". Standing in
     * the stone under a Forest hides Forest, correctly; but climbing out into that same
     * Forest is the arrival, and it was being swallowed because lastBiome already said
     * Forest. The title then only appeared after leaving for another biome and coming
     * back, which is exactly what was reported.
     */
    private static boolean hiddenUnderground;

    /**
     * Was the player gliding on the previous tick that was considered?
     *
     * <p>The gliding decline leaves {@link #lastBiome} alone on purpose, so that the biome
     * the player LANDS in still counts as a crossing. That works whenever the landing
     * biome differs from the takeoff one, and silently does nothing when it does not:
     * taking off from a Plains and landing in another Plains read as "no change" and
     * announced nothing, even though the flight was the whole journey. This makes the
     * landing itself the event.
     */
    private static boolean wasGliding;

    /**
     * The connection this state belongs to, which is how a PORTAL is told from a REJOIN.
     *
     * <p>Going through a portal tears the client level down and builds a new one, so
     * there are ticks where {@code minecraft.level} or {@code minecraft.player} is null.
     * Resetting on those - which is the obvious thing to do, and what this class did -
     * wipes lastDimension, so the dimension change is never seen and the title is
     * silently skipped: entering the Nether announced the biome and never "The Nether".
     *
     * <p>The connection survives a dimension change and does NOT survive leaving the
     * world, so comparing it is exactly the distinction that was needed.
     */
    private static ClientPacketListener session;

    private TitleTracker() {
    }

    /** Called when the player leaves a world, so a rejoin behaves like a fresh join. */
    public static void reset() {
        lastBiome = null;
        lastDimension = null;
        joinedThisSession = false;
        hiddenUnderground = false;
        wasGliding = false;
        recent.clear();
        TitleState.clear();
    }

    /** True while the player is somewhere, so the tick below has something to look at. */
    private static boolean inAWorld(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != session) {
            session = connection;
            reset();
        }
        return minecraft.player != null && minecraft.level != null;
    }

    public static void tick(Minecraft minecraft) {
        TitleState.tick();

        if (!TravelerConfig.enabled) {
            return;
        }
        if (!inAWorld(minecraft)) {
            // Nothing is reset here on purpose: a null level is a portal about as often
            // as it is the main menu, and only the connection can tell them apart.
            return;
        }
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        BlockPos pos = player.blockPosition();
        // AN UNLOADED CHUNK ANSWERS "PLAINS", IN EVERY DIMENSION. ClientChunkCache holds a
        // single EmptyLevelChunk built with Biomes.PLAINS and hands it out for any chunk it
        // does not have, so level.getBiome on a chunk that has not arrived yet is not a
        // failure that can be detected from the value: it is a confident, wrong answer, and
        // the same one in the Nether and the End. Teleporting therefore announced "Plains"
        // and then the real biome a moment later, which is exactly what was reported.
        //
        // Returning here rather than declining further down is deliberate: lastBiome,
        // lastDimension and joinedThisSession are all still untouched, so the real biome
        // reads as the change once the chunk lands. Consuming joinedThisSession on a
        // phantom Plains would also have broken "skip the biome you spawn in".
        if (!isChunkLoaded(level, pos.getX(), pos.getZ())) {
            return;
        }
        Holder<Biome> holder = level.getBiome(pos);
        Identifier biome = holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
        if (biome == null) {
            return;
        }
        Identifier dimension = level.dimension().identifier();

        boolean firstSinceJoin = !joinedThisSession;
        joinedThisSession = true;

        // lastDimension is null on the first tick in a world, and that is NOT a change:
        // treating it as one would skip the biome branch entirely and quietly override
        // hideOnWorldJoin for anyone who had turned it off.
        boolean changedDimension = lastDimension != null && !dimension.equals(lastDimension);
        lastDimension = dimension;
        if (changedDimension) {
            // NO TITLE SURVIVES A DIMENSION CHANGE, and this has to happen before the
            // dimension title is offered rather than after.
            //
            // Going through a portal while a biome title was still on screen used to keep
            // that title, in the new dimension, AND lose the dimension title: the queue
            // holds one, a newer arrival replaces it, so the biome of the dimension just
            // entered overwrote the pending dimension title before it ever started.
            // Clearing first means the old world's title leaves the screen at once and
            // the dimension title fades in from nothing, which is what a portal should
            // look like. The biome then queues behind it as designed.
            //
            // Done unconditionally, not just when dimension titles are on: a title from
            // the world you have left should not linger in the new one either way.
            TitleState.clear();
        }
        if (changedDimension && TravelerConfig.showDimensionTitles
                && !TravelerConfig.isSilenced(dimension.toString())) {
            // THE DIMENSION INTRODUCES THE BIOME, IT DOES NOT REPLACE IT. This used to
            // set lastBiome and return, so a portal announced the place and then skipped
            // the biome you arrived in - and with dimension titles switched off, which is
            // the default, it announced nothing at all. Falling through instead lets the
            // biome queue up behind the dimension; TitleState gives that pair a longer
            // gap than usual so the dimension can fade out before the biome arrives.
            show(dimension, "dimension." + dimension.getNamespace() + "."
                    + dimension.getPath(), false, true);
        }

        // Read before the same-biome return, because landing is the event even when the
        // biome underneath has not changed.
        boolean glidingNow = TravelerConfig.hideWhileGliding && player.isFallFlying();
        boolean justLanded = wasGliding && !glidingNow;

        if (biome.equals(lastBiome)) {
            // Normally there is nothing to do: the biome has not changed. The exception
            // is a biome that was HIDDEN underground, which the player has still never
            // been shown, so surfacing into it has to count as the arrival.
            //
            // Gated on the cheap half of the underground test - one heightmap lookup -
            // rather than on the full one. That is what keeps the promise in the comment
            // this replaces: the 49 column sweep still does not run on every tick the
            // player spends standing underground, only once they have actually climbed
            // out. Without the gate this would be the most expensive path in the mod.
            boolean surfacedIntoHidden = hiddenUnderground && !isBelowSurface(level, pos);
            if (!surfacedIntoHidden && !justLanded) {
                return;
            }
        }

        // BEFORE lastBiome is updated, deliberately, unlike every other reason to
        // decline below. Gliding is a temporary state rather than a decision about this
        // biome, so leaving lastBiome alone means the border is still un-crossed as far
        // as the tracker is concerned and the biome the player LANDS in gets announced.
        // Silence during the flight and then nothing at all afterwards would be a worse
        // reading of "hide while flying" than silence during the flight.
        if (glidingNow) {
            wasGliding = true;
            return;
        }
        wasGliding = false;

        // Set before the rest, so a biome that was declined is not reconsidered on every
        // tick the player stands in it. That matters most for the underground rule,
        // whose test walks a column of blocks. The one decline that must survive this is
        // the underground one, which records itself in hiddenUnderground below.
        lastBiome = biome;
        hiddenUnderground = false;

        // "You didn't travel anywhere." The biome you spawn into is not a discovery.
        if (firstSinceJoin && TravelerConfig.hideOnWorldJoin) {
            return;
        }
        if (TravelerConfig.isSilenced(biome.toString())) {
            return;
        }

        boolean asPlainText = false;
        if (TravelerConfig.undergroundTitles != TravelerConfig.UndergroundTitles.SHOW
                && isSurfaceBiomeSeenFromUnderground(level, pos, biome)) {
            if (TravelerConfig.undergroundTitles == TravelerConfig.UndergroundTitles.HIDE) {
                // Remembered so that climbing out into this same biome re-announces it.
                // Only the HIDE branch does this: PLAIN_TEXT has already put a title on
                // screen, and showing the artwork again on the way up would be the same
                // place announced twice.
                hiddenUnderground = true;
                return;
            }
            asPlainText = true;
        }

        show(biome, "biome." + biome.getNamespace() + "." + biome.getPath(), asPlainText, false);
    }

    /**
     * Has the client actually received the chunk covering these block coordinates?
     *
     * <p>ASK THE CHUNK SOURCE, NEVER THE LEVEL. {@code ClientLevel.hasChunk} is
     * literally {@code return true} - one instruction, no lookup - because the client
     * treats every position in render distance as present. It overrides
     * LevelReader.hasChunk, so calling it compiles, reads correctly and answers yes for
     * a chunk that has not arrived, which made the first version of this guard a no-op
     * that shipped and changed nothing.
     *
     * <p>{@code ChunkSource.hasChunk} is the real test: {@code getChunk(x, z, FULL,
     * false) != null}, and ClientChunkCache returns null there in exactly the case where
     * the loading form would hand back its Plains-filled EmptyLevelChunk. ClientChunkCache
     * does not override it. Both facts verified by disassembly at 26.1.2 and 26.2.
     */
    private static boolean isChunkLoaded(ClientLevel level, int blockX, int blockZ) {
        return level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4);
    }

    /**
     * Is the player underground, in a biome that also exists at the surface?
     *
     * <p>This is the whole of the cave rule and it needs no list of cave biomes, which
     * matters twice over: there is no {@code minecraft:is_cave} tag to read, and a
     * client-only mod could not ship one anyway because biome tags come from the server.
     */
    private static boolean isSurfaceBiomeSeenFromUnderground(
            ClientLevel level, BlockPos pos, Identifier biome) {
        if (!isBelowSurface(level, pos)) {
            return false;
        }
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        if (!hasSolidCeiling(level, pos, surface)) {
            return false;
        }
        return reachesTheSurfaceNearby(level, pos, biome);
    }

    /**
     * Is the player far enough below ground level for the cave rule to be worth asking?
     *
     * <p>The cheap half of the test above, and deliberately ONE source of truth for it:
     * the re-announce gate and the cave rule have to agree about what "underground"
     * means, or a biome could be hidden on the way down and never re-offered on the way
     * up. One heightmap lookup, so it is affordable per tick.
     */
    private static boolean isBelowSurface(ClientLevel level, BlockPos pos) {
        // The Nether has no sky and its heightmap is the ceiling, so every position in
        // it would read as underground and the rule would silence the whole dimension.
        if (!level.dimensionType().hasSkyLight()) {
            return false;
        }
        // MOTION_BLOCKING_NO_LEAVES rather than canSeeSky, which uses MOTION_BLOCKING
        // and so counts a leaf canopy as a ceiling - the incumbent's open bug where a
        // Dark Forest never announces itself.
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return pos.getY() < surface - UNDERGROUND_DEPTH;
    }

    /**
     * Does this biome also exist at ground level anywhere close by?
     *
     * <p>ONE COLUMN IS NOT ENOUGH, and that is what shipped first: the rule compared the
     * player's biome with the biome at the top of their own column, which is right in
     * the steady state and wrong at exactly the moment the rule is consulted. Biomes are
     * THREE dimensional, so the boundary between two surface biomes underground sits
     * tens of blocks away from where the same boundary sits at the surface. Crossing one
     * at depth therefore compares the biome you have just entered against the biome still
     * overhead, finds them different, and announces a surface biome in a cave - which is
     * precisely what was reported, on both loaders and both versions.
     *
     * <p>Sampling a grid of columns instead asks the question that was always meant:
     * does this biome occur at the surface around here at all? A cave biome does not,
     * anywhere, so it still shows; a surface biome does, so it still hides.
     *
     * <p>Runs only when the biome CHANGES, never per frame, so 49 heightmap and biome
     * lookups are affordable and there is no reason to approximate.
     */
    private static boolean reachesTheSurfaceNearby(
            ClientLevel level, BlockPos pos, Identifier biome) {
        int floor = level.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx : SAMPLE_OFFSETS) {
            for (int dz : SAMPLE_OFFSETS) {
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                // Ask whether the chunk is there, rather than inferring it from a
                // suspicious heightmap. An unloaded chunk does answer with the bottom of
                // the world, but it also answers PLAINS for the biome, so a column that
                // slipped past a sentinel check could match a Plains player and wrongly
                // hide a legitimate title. The floor test stays as well: a loaded chunk
                // can still be genuinely empty.
                if (!isChunkLoaded(level, x, z)) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (top <= floor + 1) {
                    continue;
                }
                Identifier there = level.getBiome(cursor.set(x, top, z))
                        .unwrapKey().map(ResourceKey::identifier).orElse(null);
                if (biome.equals(there)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Is there real terrain between the player and the sky, rather than just water?
     *
     * <p>Depth alone is not enough. MOTION_BLOCKING_NO_LEAVES counts fluids, so the top
     * of the sea is a "surface" and a player forty blocks down in a Deep Ocean would
     * read as underground - and since the biome above them is the same ocean, the rule
     * would swallow every underwater biome title. Requiring one non-fluid, motion
     * blocking block in the column fixes that, and it also lets a ravine that is open to
     * the sky keep its titles.
     *
     * <p>Runs only when the biome changes, never per frame, so walking the column is
     * cheap enough to be the honest test rather than an approximation of one.
     */
    private static boolean hasSolidCeiling(ClientLevel level, BlockPos pos, int surface) {
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        // From just above the player's head, so the block they are standing under does
        // not count on its own.
        for (int y = pos.getY() + 2; y < surface; y++) {
            BlockState state = level.getBlockState(cursor.setY(y));
            if (state.blocksMotion() && state.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void show(Identifier subject, String translationKey, boolean forceText,
                             boolean dimension) {
        TitleArt.Entry artwork = forceText ? null : TitleArt.artworkFor(subject);
        Component component = artwork != null
                ? TitleArt.artworkComponent(artwork)
                : TitleArt.plainText(translationKey, subject);

        // SUPPRESS ON THE DISPLAYED NAME, NOT THE ID. Chaotic biome borders re-trigger
        // constantly, and the incumbent's own landing fix for that was to compare the
        // translated value so that any two biomes showing the same name count as one.
        // It also means a pack author can group biomes purely with a lang file. Taking
        // the name from the translation rather than from the artwork keeps the key the
        // same whether or not this particular showing was forced to plain text.
        String name = Component.translatable(translationKey).getString();

        switch (TravelerConfig.showTitleWhen) {
            case FIRST_TIME_ONLY -> {
                if (!everShown.add(name)) {
                    return;
                }
            }
            case NOT_SEEN_RECENTLY -> {
                if (recent.contains(name)) {
                    return;
                }
                recent.addLast(name);
                while (recent.size() > Math.max(0, TravelerConfig.rememberLastTitles)) {
                    recent.removeFirst();
                }
            }
            case ALWAYS -> {
            }
        }

        Constants.LOG.debug("Title for {}{}", subject, artwork != null ? " (artwork)" : " (text)");
        TitleState.show(name, component, artwork, dimension);
    }
}
