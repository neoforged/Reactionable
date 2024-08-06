package net.neoforged.automation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record StartupConfiguration(Properties properties) {
    public static StartupConfiguration load(Path path) throws IOException {
        var props = new Properties();
        try (var is = Files.newInputStream(path)) {
            props.load(is);
        }
        return new StartupConfiguration(props);
    }

    public String get(String key, String defaultValue) {
        try {
            var prop = properties.getProperty(key, defaultValue);
            if (prop.startsWith("file:")) {
                return Files.readString(Path.of(prop.substring(5)));
            }
            if (prop.startsWith("env:")) {
                return System.getenv(prop.substring(4));
            }
            return prop;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }
}
