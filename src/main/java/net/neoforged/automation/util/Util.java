package net.neoforged.automation.util;

import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Util {
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    public static String[] readLines(URL url) {
        try (var in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
        } catch (Exception ex) {
            sneakyThrow(ex);
            return new String[0];
        }
    }
}
