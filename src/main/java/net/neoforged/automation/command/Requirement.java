package net.neoforged.automation.command;

import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.util.FunctionalInterfaces;
import org.kohsuke.github.GHPermissionType;

public enum Requirement implements FunctionalInterfaces.PredException<GHCommandContext> {
    IS_PR(ctx -> ctx.issue().isPullRequest()),
    IS_MAINTAINER(ctx -> ctx.repository().hasPermission(ctx.user(), GHPermissionType.WRITE)),
    IS_AUTHOR(ctx -> ctx.user().equals(ctx.issue().getUser()));

    private final FunctionalInterfaces.PredException<GHCommandContext> test;

    Requirement(FunctionalInterfaces.PredException<GHCommandContext> test) {
        this.test = test;
    }

    @Override
    public boolean test(GHCommandContext ghCommandContext) throws Exception {
        return test.test(ghCommandContext);
    }
}
