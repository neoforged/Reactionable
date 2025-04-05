package net.neoforged.automation;

import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.neoforged.automation.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public record FileHostService(Path baseFolder, StartupConfiguration configuration) {
    public FileHostService {
        try {
            Files.createDirectories(baseFolder);
        } catch (IOException e) {
            Util.sneakyThrow(e);
        }
    }

    public void get(Context context) {
        var file = context.pathParam("file");

        try {
            //noinspection ResultOfMethodCallIgnored
            UUID.fromString(file); // this is lame validation

            context.result(Files.newInputStream(baseFolder.resolve(file)));
            context.contentType(ContentType.APPLICATION_JSON);
        } catch (Exception ex) {
            context.status(HttpStatus.NOT_FOUND);
        }
    }

    public String upload(String content) {
        var id = UUID.randomUUID();
        try {
            Files.writeString(baseFolder.resolve(id.toString()), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Util.sneakyThrow(e);
        }
        return configuration.resolveUrl("serverUrl", "/file/" + id);
    }
}
