package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;

/** Shared style for settings section badges. Colors follow the active theme at draw time. */
public final class SettingsIconColors {
    public static final int NEUTRAL = 0, ACCENT = 1, ORIGINAL = 2, CUSTOM = 3;
    private SettingsIconColors() {}
    public static int mode() {
        if (ColorOs17Theme.isEnabled()) return ORIGINAL;
        int value = NimarkoConfig.getPreferences().getInt("settingsIconColorMode", NEUTRAL);
        return value >= NEUTRAL && value <= CUSTOM ? value : NEUTRAL;
    }
    /** A flat plate a little darker than the settings list background; no glass, no gradient. */
    public static int plateColor(int listBackground, boolean dark) {
        return androidx.core.graphics.ColorUtils.blendARGB(listBackground, 0xFF000000, dark ? 0.30f : 0.12f);
    }
    public static int glyphColor(boolean dark) {
        return dark ? 0xFFF2F2F7 : 0xFF17171A;
    }
    public static int customColor() {
        return NimarkoConfig.getPreferences().getInt("settingsIconCustomColor", 0xFF7E72DE) | 0xFF000000;
    }
    public static void setMode(int mode) {
        NimarkoConfig.getEditor().putInt("settingsIconColorMode", mode).apply();
    }
    public static void setCustomColor(int color) {
        NimarkoConfig.getEditor().putInt("settingsIconCustomColor", color | 0xFF000000)
                .putInt("settingsIconColorMode", CUSTOM).apply();
    }
}
