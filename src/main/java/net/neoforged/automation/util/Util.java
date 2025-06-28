package net.neoforged.automation.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;

public class Util {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    public static <T> T readAs(URI uri, Class<T> type) throws IOException {
        return MAPPER.readValue(uri.toURL(), type);
    }
}
