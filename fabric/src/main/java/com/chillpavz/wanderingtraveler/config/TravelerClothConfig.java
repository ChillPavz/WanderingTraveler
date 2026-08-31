package com.chillpavz.wanderingtraveler.config;

import com.chillpavz.wanderingtraveler.Constants;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.shedaniel.autoconfig.util.Utils;
import me.shedaniel.clothconfig2.api.AbstractConfigEntry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.ColorEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListListEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Cloth Config screen, and the file behind it.
 *
 * <p>THIS FILE IS DUPLICATED, BYTE FOR BYTE, IN EVERY LOADER MODULE, and it has to be:
 * {@code common} compiles against a bare NeoForm vanilla jar with no loader dependencies
 * on its classpath, so it cannot see Cloth at all. The copies never share a classpath, so
 * they can and do keep the same package and class name, and {@code tools/audit_jar.py}
 * diffs them so that a change made to one and not the other cannot ship.
 *
 * <p>Every value is pushed into {@link TravelerConfig}, which is the only thing the rest
 * of the mod reads. Nothing outside this file mentions Cloth.
 *
 * <h2>THIS CLASS MAY DECLARE NO FIELD THAT IS NOT AN OPTION</h2>
 * {@code ConfigScreenProvider} walks {@code getDeclaredFields()} with NO filter for
 * static, transient or synthetic fields, so a static bookkeeping flag became a real entry
 * in the screen: an untranslated toggle in an untranslated "default" tab, because it had
 * no {@code @Category} and no lang key. Anything that is not a player-facing option lives
 * in {@link Plumbing} below, which is a separate class and so is not a field of this one.
 *
 * <h2>Naming</h2>
 * Options are named after what the player wants, not after the data structure behind
 * them. The incumbent calls its repeat suppression "Number of Most Recent Biomes Saved"
 * and three separate people opened issues because they could not find it or work out
 * what 0 meant; that is the mistake this naming exists to avoid.
 */
@Config(name = Constants.MOD_ID)
public class TravelerClothConfig implements ConfigData {

    // ------------------------------------------------------------------- general
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    public boolean showBiomeTitles = true;

    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip
    public boolean useArtwork = true;

    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public boolean showDimensionTitles = true;

    /**
     * Switches off every tooltip in this screen, including its own.
     *
     * <p>OFF by default. A tooltip is drawn at the cursor, so on a slider it lands over
     * the value you are dragging to. The first attempt at that was to drop the tooltips
     * from the sliders and move the explanation into a prefix line above each row, which
     * worked and made the screen twice as long; a switch that hides all of them keeps the
     * screen compact and the detail one click away.
     */
    @ConfigEntry.Category("general")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public boolean showTooltips = false;

    // ---------------------------------------------------------------- appearance
    /**
     * Three size sliders rather than one, because a long name at the scale that suits a
     * short one runs into the screen edge. The band is chosen by the title's drawn width
     * in font units: 85 and below is short, up to 120 is medium, above that is long.
     */
    @ConfigEntry.Category("appearance")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 600)
    @ConfigEntry.Gui.Tooltip
    public int sizeShortNames = 300;

    @ConfigEntry.Category("appearance")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 600)
    @ConfigEntry.Gui.Tooltip
    public int sizeMediumNames = 275;

    @ConfigEntry.Category("appearance")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 600)
    @ConfigEntry.Gui.Tooltip
    public int sizeLongNames = 250;

    @ConfigEntry.Category("appearance")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 600)
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int sizePlainText = 150;

    // Plain text only. A full-colour glyph is MULTIPLIED by the text colour, so these two
    // cannot apply to the artwork: tinting it would only darken it, and it draws its own
    // shadow in the art.
    @ConfigEntry.Category("appearance")
    @ConfigEntry.ColorPicker(allowAlpha = false)
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int plainTextColour = 0xFFFFFF;

    @ConfigEntry.Category("appearance")
    @ConfigEntry.Gui.Tooltip
    public boolean plainTextShadow = true;

    @ConfigEntry.Category("appearance")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public boolean deriveShadowColourFromText = true;

    /**
     * Ignored entirely while the toggle above is on, and set to the derived colour every
     * time the screen is opened so that you can SEE what was picked.
     */
    @ConfigEntry.Category("appearance")
    @ConfigEntry.ColorPicker(allowAlpha = false)
    @ConfigEntry.Gui.Tooltip(count = 2)
    public int plainTextShadowColour = TravelerConfig.DEFAULT_SHADOW_COLOR;

    // ----------------------------------------------------------------- placement
    @ConfigEntry.Category("placement")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    @ConfigEntry.Gui.Tooltip(count = 2)
    public Anchor cornerOfTheScreen = Anchor.TOP_CENTER;

    // Deliberately a number box rather than a slider: placement is the one thing people
    // nudge by single pixels, and dragging a slider across a thousand values to find 12
    // is worse than typing it. The incumbent's open complaint here is that its offsets
    // are undocumented and move with the GUI scale, not that they are hard to drag.
    @ConfigEntry.Category("placement")
    @ConfigEntry.Gui.Tooltip
    public int offsetRight = 0;

    @ConfigEntry.Category("placement")
    @ConfigEntry.Gui.Tooltip
    public int offsetDown = 0;

    // -------------------------------------------------------------------- timing
    @ConfigEntry.Category("timing")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 60)
    @ConfigEntry.Gui.Tooltip
    public int fadeInTicks = 8;

    @ConfigEntry.Category("timing")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 400)
    @ConfigEntry.Gui.Tooltip
    public int timeOnScreenTicks = 40;

    @ConfigEntry.Category("timing")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 60)
    @ConfigEntry.Gui.Tooltip
    public int fadeOutTicks = 16;

    @ConfigEntry.Category("timing")
    @ConfigEntry.BoundedDiscrete(min = 0, max = 200)
    @ConfigEntry.Gui.Tooltip(count = 3)
    public int minimumTimeBetweenTitles = 50;

    // ---------------------------------------------------------------- situations
    @ConfigEntry.Category("situations")
    @ConfigEntry.Gui.Tooltip
    public boolean hideWhileFlyingWithElytra = false;

    @ConfigEntry.Category("situations")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    @ConfigEntry.Gui.Tooltip(count = 2)
    public TravelerConfig.UndergroundTitles surfaceBiomesWhileUnderground =
            TravelerConfig.UndergroundTitles.HIDE;

    // --------------------------------------------------------------- suppression
    @ConfigEntry.Category("suppression")
    @ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
    @ConfigEntry.Gui.Tooltip(count = 2)
    public TravelerConfig.ShowTitleWhen showATitle = TravelerConfig.ShowTitleWhen.ALWAYS;

    @ConfigEntry.Category("suppression")
    @ConfigEntry.BoundedDiscrete(min = 1, max = 50)
    @ConfigEntry.Gui.Tooltip
    public int rememberLastTitles = 5;

    @ConfigEntry.Category("suppression")
    @ConfigEntry.Gui.Tooltip
    public boolean skipTheBiomeYouSpawnIn = false;

    @ConfigEntry.Category("suppression")
    @ConfigEntry.Gui.Tooltip(count = 2)
    public List<String> neverShowTheseBiomes = new ArrayList<>();

    /**
     * Copy every value into the holder the rest of the mod reads.
     *
     * <p>One direction only. Nothing writes back into these fields except Cloth itself,
     * so there is no second source of truth to drift.
     */
    public void apply() {
        TravelerConfig.enabled = showBiomeTitles;
        TravelerConfig.useArtwork = useArtwork;
        TravelerConfig.showDimensionTitles = showDimensionTitles;
        TravelerConfig.showTooltips = showTooltips;

        TravelerConfig.artworkScaleShort = sizeShortNames;
        TravelerConfig.artworkScaleMedium = sizeMediumNames;
        TravelerConfig.artworkScaleLong = sizeLongNames;
        TravelerConfig.textScalePercent = sizePlainText;
        TravelerConfig.textColor = plainTextColour;
        TravelerConfig.textShadow = plainTextShadow;
        TravelerConfig.deriveShadowColor = deriveShadowColourFromText;
        TravelerConfig.shadowColor = plainTextShadowColour;

        TravelerConfig.anchor = cornerOfTheScreen;
        TravelerConfig.offsetX = offsetRight;
        TravelerConfig.offsetY = offsetDown;

        TravelerConfig.fadeInTicks = fadeInTicks;
        TravelerConfig.holdTicks = timeOnScreenTicks;
        TravelerConfig.fadeOutTicks = fadeOutTicks;
        TravelerConfig.minTicksBetweenTitles = minimumTimeBetweenTitles;

        TravelerConfig.hideWhileGliding = hideWhileFlyingWithElytra;
        TravelerConfig.undergroundTitles = surfaceBiomesWhileUnderground;

        TravelerConfig.showTitleWhen = showATitle;
        TravelerConfig.rememberLastTitles = rememberLastTitles;
        TravelerConfig.hideOnWorldJoin = skipTheBiomeYouSpawnIn;
        TravelerConfig.setNeverShow(neverShowTheseBiomes);
    }

    /**
     * Everything that is not a player-facing option.
     *
     * <p>A separate class ON PURPOSE: see the note on the class above. A field here is
     * not a field of {@link TravelerClothConfig}, so Cloth never sees it.
     */
    public static final class Plumbing {

        /** What a new row in the biome list starts as, so the box is visible to type in. */
        private static final String NEW_BIOME_PLACEHOLDER = "minecraft:";

        /** The one field the list provider is registered against. */
        private static final String BIOME_LIST_FIELD = "neverShowTheseBiomes";

        private static boolean registered;

        private Plumbing() {
        }

        /**
         * Register the config, load it and push it into {@link TravelerConfig}.
         *
         * <p>DEGRADE, NEVER CRASH. A config file that a player has hand-edited into
         * something Gson cannot read must cost the config screen, not the mod: the titles
         * are the product and they work perfectly on the defaults.
         */
        public static void register() {
            try {
                ConfigHolder<TravelerClothConfig> holder = AutoConfig.register(
                        TravelerClothConfig.class, GsonConfigSerializer::new);
                // The save listener returns net.minecraft.world.InteractionResult, not a
                // Cloth type - one of the few places Cloth hands back a vanilla class.
                holder.registerSaveListener((h, config) -> {
                    config.apply();
                    return InteractionResult.SUCCESS;
                });
                holder.getConfig().apply();

                // A class-specific GuiRegistry is consulted BEFORE Cloth's defaults, so
                // this replaces the stock string-list widget for that one field.
                AutoConfig.getGuiRegistry(TravelerClothConfig.class)
                        .registerPredicateProvider(Plumbing::biomeList,
                                field -> BIOME_LIST_FIELD.equals(field.getName()));
                registered = true;
            } catch (Throwable t) {
                Constants.LOG.error("Could not set up the config screen; running on defaults", t);
            }
        }

        /**
         * The biome exclusion list, with a placeholder in every new row.
         *
         * <p>Cloth's stock string list adds an EMPTY cell when you press +, and an empty
         * text field in that list draws no border at all - the owner's report was that it
         * looks as though there is nowhere to type. Starting each row at
         * {@value #NEW_BIOME_PLACEHOLDER} makes the box visible and shows the format at
         * the same time, which is a better answer than explaining it in a tooltip.
         *
         * <p>Everything else here is what {@code DefaultGuiProviders} does for a
         * {@code List<String>}: the name, the default for the reset button, and the save
         * consumer that writes back through reflection.
         */
        private static List<AbstractConfigListEntry> biomeList(
                String i18n, Field field, Object config, Object defaults,
                GuiRegistryAccess registry) {
            List<String> value = Utils.getUnsafely(field, config, new ArrayList<String>());
            List<String> fallback = Utils.getUnsafely(field, defaults);
            return Collections.singletonList(ConfigEntryBuilder.create()
                    .startStrList(Component.translatable(i18n), value)
                    .setDefaultValue(fallback)
                    .setSaveConsumer(list -> Utils.setUnsafely(field, config, list))
                    .setCreateNewInstance(entry -> new StringListListEntry.StringListCell(
                            NEW_BIOME_PLACEHOLDER, entry))
                    .setExpanded(true)
                    .build());
        }

        /**
         * The config screen.
         *
         * <p>Falls back to the screen the player came from rather than to null: both
         * loaders hand this straight to a Config button and neither promises to survive a
         * null, so the degraded outcome is a button that goes nowhere - with a line
         * already in the log - rather than a crash out of the mods list.
         *
         * <p>{@code AutoConfig}, not {@code AutoConfigClient}: cloth ships THREE majors
         * across this jar's range - 19.0.x, 20.0.x and 21.11.x - and AutoConfigClient does
         * not exist below 21.11.x. AutoConfig.getConfigScreen is present on every cloth
         * artifact this jar can meet, so it is the one call that works everywhere here.
         * The 1.21.11 and 26.x branches use AutoConfigClient instead, and on 1.21.11 Forge
         * they MUST: the unofficial cloth-config-forge port strips the screen methods off
         * AutoConfig entirely.
         */
        public static Screen screen(Screen parent) {
            if (registered) {
                try {
                    Screen screen = AutoConfig.getConfigScreen(
                            TravelerClothConfig.class, parent).get();
                    gateTooltips(screen);
                    showDerivedShadow(screen);
                    return screen;
                } catch (Throwable t) {
                    Constants.LOG.error("Could not open the config screen", t);
                }
            }
            return parent;
        }

        /**
         * Put every tooltip in the screen behind the "Show Tooltips" option.
         *
         * <p>DONE ON THE BUILT SCREEN, not with a GuiTransformer, and that is forced
         * rather than chosen: {@code ComposedGuiRegistryAccess} chains its registries in
         * order and the class-specific one comes FIRST, so a transformer registered there
         * runs BEFORE Cloth attaches the tooltip it is meant to remove. The finished
         * screen is the only place where every tooltip already exists.
         *
         * <p>The supplier is wrapped rather than cleared, and it reads the live value, so
         * turning the option off applies the moment the change is saved instead of only
         * the next time the screen is opened.
         */
        /**
         * Fill the shadow colour field in with the colour the derivation actually picks.
         *
         * <p>An inert field showing a stale value is worse than no field, so when the
         * derive toggle is on the entry is set to {@code deriveShadow(textColour)} as the
         * screen is built. Change the text colour, save, and the shadow field shows the
         * new derived colour when you come back.
         *
         * <p>ON THE BUILT SCREEN, not per frame, and that is a deliberate limit. Cloth
         * has no per-frame seam that does not mean subclassing {@code ColorEntry} and
         * building it by hand, which would couple this file to a Cloth constructor for a
         * refresh you get anyway by reopening the screen.
         *
         * <p>Entries are found by their DISPLAYED NAME rather than by position, because
         * position changes whenever an option is added and would fail silently.
         */
        private static void showDerivedShadow(Screen screen) {
            if (!TravelerConfig.deriveShadowColor
                    || !(screen instanceof ClothConfigScreen cloth)) {
                return;
            }
            AbstractConfigEntry<?> source = find(cloth, "plainTextColour");
            AbstractConfigEntry<?> target = find(cloth, "plainTextShadowColour");
            if (source != null && target instanceof ColorEntry colour
                    && source.getValue() instanceof Integer text) {
                colour.setValue(TravelerConfig.deriveShadow(text));
            }
        }

        private static AbstractConfigEntry<?> find(ClothConfigScreen screen, String option) {
            String label = Component.translatable(
                    "text.autoconfig." + Constants.MOD_ID + ".option." + option).getString();
            for (List<AbstractConfigEntry<?>> entries
                    : screen.getCategorizedEntries().values()) {
                for (AbstractConfigEntry<?> entry : entries) {
                    if (label.equals(entry.getFieldName().getString())) {
                        return entry;
                    }
                }
            }
            return null;
        }

        private static void gateTooltips(Screen screen) {
            if (!(screen instanceof ClothConfigScreen cloth)) {
                return;
            }
            for (List<AbstractConfigEntry<?>> entries : cloth.getCategorizedEntries().values()) {
                for (AbstractConfigEntry<?> entry : entries) {
                    if (!(entry instanceof TooltipListEntry<?> tooltipped)) {
                        continue;
                    }
                    Supplier<Optional<Component[]>> original = tooltipped.getTooltipSupplier();
                    if (original == null) {
                        continue;
                    }
                    tooltipped.setTooltipSupplier(
                            () -> TravelerConfig.showTooltips ? original.get() : Optional.empty());
                }
            }
        }
    }
}
