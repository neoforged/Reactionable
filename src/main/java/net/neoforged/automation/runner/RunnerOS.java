package net.neoforged.automation.runner;

import java.util.Locale;

public enum RunnerOS {
    UBUNTU,
    WINDOWS,
    MACOS;

    final String latest;
    RunnerOS() {
        this.latest = name().toLowerCase(Locale.ROOT) + "-latest";
    }
}
