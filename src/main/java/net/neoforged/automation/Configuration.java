package net.neoforged.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public record Configuration(
        Map<String, RepoConfiguration> repositories
) {
    public record RepoConfiguration(Map<String, LabelLock> labelLocks) {

    }

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE));

    private static volatile Configuration configuration = new Configuration(Map.of());

    public static void load(GitHub gitHub, RepoLocation location) throws IOException {
        configuration = getOrCommit(gitHub.getRepository(location.repo()), location.path(), location.branch());
    }

    public static RepoLocation load(GitHub gitHub, StartupConfiguration startupConfiguration) throws IOException {
        var configLocation = startupConfiguration.get("configurationLocation", "");
        final var location = RepoLocation.parse(configLocation);
        load(gitHub, location);
        return location;
    }

    private static Configuration getOrCommit(GHRepository repository, String path, String branch) throws IOException {
        try (final var is = repository.getFileContent(path, branch).read()) {
            return MAPPER.readValue(is, Configuration.class).sanitize();
        }
    }

    private Configuration sanitize() {
        return new Configuration(repositories.entrySet()
                .stream().collect(Collectors.toMap(f -> f.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue)));
    }

    public static RepoConfiguration get(GHRepository repository) {
        return configuration.repositories().get(repository.getFullName().toLowerCase(Locale.ROOT));
    }

    public record RepoLocation(String repo, String path, String branch) {
        public static RepoLocation parse(String str) {
            final String[] repoAndDirBranch = str.split(":");
            final String[] dirAndBranch = repoAndDirBranch[1].split("@");
            return new RepoLocation(repoAndDirBranch[0], dirAndBranch[0], dirAndBranch[1]);
        }
    }

    public record LabelLock(
            boolean lock,
            @Nullable String lockReason,
            boolean close,
            @Nullable String message
    ) {}
}
