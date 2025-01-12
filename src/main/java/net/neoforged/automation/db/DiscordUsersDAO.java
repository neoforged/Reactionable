package net.neoforged.automation.db;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transactional;
import org.jetbrains.annotations.Nullable;

public interface DiscordUsersDAO extends Transactional<DiscordUsersDAO> {
    @Nullable
    @SqlQuery("select github from discord_users where user = :user")
    String getUser(@Bind("user") long id);

    @SqlUpdate("insert into discord_users (user, github) values (:user, :github)")
    void linkUser(@Bind("user") long id, @Bind("github") String githubName);
}
