package net.neoforged.automation.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Util {
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    public static String[] readLines(InputStream i) {
        try (var in = i) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
        } catch (Exception ex) {
            sneakyThrow(ex);
            return new String[0];
        }
    }
}
