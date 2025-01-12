package net.neoforged.automation.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import net.neoforged.automation.Main;
import net.neoforged.automation.db.Database;
import net.neoforged.automation.db.DiscordUsersDAO;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GitHubBuilder;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public record GitHubOauthLinkHandler(DiscordBot.OauthConfig config) implements Handler {
    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        var code = ctx.queryParam("code");
        var state = ctx.queryParam("state");
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            ctx.result("Wrong input provided")
                    .status(HttpStatus.BAD_REQUEST);
            return;
        }

        var mapper = new ObjectMapper();
        var node = mapper.createObjectNode();
        node.put("client_id", config.clientId());
        node.put("client_secret", config.clientSecret());
        node.put("grant_type", "authorization_code");
        node.put("redirect_uri", config.redirectUrl());
        node.put("scope", "user:read");
        node.put("code", code);

        var req = Main.HTTP_CLIENT.send(HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(node.toString()))
                .uri(URI.create("https://github.com/login/oauth/access_token"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build(), HttpResponse.BodyHandlers.ofString());

        var asJson = mapper.readValue(req.body(), JsonNode.class);
        var token = asJson.get("access_token");
        if (token == null) {
            ctx.result("Error: " + req.body())
                    .status(HttpStatus.BAD_REQUEST);
            return;
        }

        var user = new GitHubBuilder()
                .withJwtToken(token.asText())
                .build()
                .getMyself()
                .getLogin();

        Database.useExtension(DiscordUsersDAO.class, db -> db.linkUser(Long.parseLong(state), user));

        ctx.result("Authenticated as " + user + ". You may close this page.").status(HttpStatus.OK);
    }
}
