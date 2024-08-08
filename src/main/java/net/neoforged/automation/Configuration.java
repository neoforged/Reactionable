package net.neoforged.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public record Configuration(
        Commands commands,
        PRActions prActions,
        Map<String, RepoConfiguration> repositories
) {
    public record RepoConfiguration(Boolean enabled, Map<String, LabelLock> labelLocks, List<String> formattingTasks) {
        public RepoConfiguration {
            enabled = enabled == null || enabled;
        }
        public static final RepoConfiguration DEFAULT = new RepoConfiguration(true, Map.of(), List.of());
    }

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE));

    private static volatile Configuration configuration = new Configuration(new Commands(List.of(), false, false), new PRActions(null, null), Map.of());

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
        return new Configuration(
                commands, prActions, repositories.entrySet()
                .stream().collect(Collectors.toMap(f -> f.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue)));
    }

    public static RepoConfiguration get(GHRepository repository) {
        return configuration.repositories().getOrDefault(repository.getFullName().toLowerCase(Locale.ROOT), RepoConfiguration.DEFAULT);
    }

    public static Configuration get() {
        return configuration;
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

    public record Commands(List<String> prefixes, boolean reactToComment, boolean minimizeComment) {}

    public record PRActions(String repository, String workflow) {}
}
