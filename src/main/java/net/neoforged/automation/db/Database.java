package net.neoforged.automation.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.ExtensionCallback;
import org.jdbi.v3.core.extension.ExtensionConsumer;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;

public class Database {

    private static Jdbi database;

    public static Jdbi get() {
        return database;
    }

    public static <R, E, X extends Exception> R withExtension(Class<E> extensionType, ExtensionCallback<R, E, X> callback) throws X {
        return get().withExtension(extensionType, callback);
    }

    public static <E, X extends Exception> void useExtension(Class<E> extensionType, ExtensionConsumer<E, X> callback) throws X {
        get().useExtension(extensionType, callback);
    }

    public static void init() {
        var path = Path.of("data.db");
        database = createDatabaseConnection(path, "reactionable", c -> c.locations("classpath:db"));
    }

    private static Jdbi createDatabaseConnection(Path dbPath, String name, UnaryOperator<FluentConfiguration> flywayConfig) {
        dbPath = dbPath.toAbsolutePath();
        if (!Files.exists(dbPath)) {
            try {
                Files.createDirectories(dbPath.getParent());
                Files.createFile(dbPath);
            } catch (IOException e) {
                throw new RuntimeException("Exception creating database!", e);
            }
        }
        final String url = "jdbc:sqlite:" + dbPath;
        final SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
        dataSource.setEncoding("UTF-8");
        dataSource.setDatabaseName(name);
        dataSource.setEnforceForeignKeys(true);
        dataSource.setCaseSensitiveLike(false);

        final var flyway = flywayConfig.apply(Flyway.configure().dataSource(dataSource)).load();
        flyway.migrate();

        return Jdbi.create(dataSource)
                .installPlugin(new SqlObjectPlugin());
    }
}
