package net.neoforged.automation.util;

public enum GHAction {
    /** Comments are created */
    CREATED,
    /** PRs and issues are opened */
    OPENED,
    REOPENED,

    LABELED,
    UNLABELED,

    SYNCHRONIZE,

    COMPLETED
}
