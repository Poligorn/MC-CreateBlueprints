package com.blueprintforge.data;

public final class DockSettingsRegistry {
    private static DockSettings current = DockSettings.DEFAULT;

    private DockSettingsRegistry() {
    }

    public static void replace(DockSettings settings) {
        current = settings == null ? DockSettings.DEFAULT : settings;
    }

    public static DockSettings get() {
        return current;
    }
}
