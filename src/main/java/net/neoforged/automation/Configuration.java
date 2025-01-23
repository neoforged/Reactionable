package net.neoforged.automation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import net.neoforged.automation.webhook.label.LabelHandler;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Configuration(
        Commands commands,
        PRActions prActions,
        Map<String, RepoConfiguration> repositories
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepoConfiguration(Boolean enabled, @JsonDeserialize(contentUsing = LabelHandler.Deserializer.class) Map<String, LabelHandler> labelHandlers, @Nullable String baseRunCommand, BackportConfiguration backport) {
        public RepoConfiguration {
            enabled = enabled == null || enabled;
            labelHandlers = labelHandlers == null ? Map.of() : labelHandlers;
            backport = backport == null ? BackportConfiguration.DEFAULT : backport;
        }
        public static final RepoConfiguration DEFAULT = new RepoConfiguration(true, Map.of(), null, BackportConfiguration.DEFAULT);

        @Nullable
        public <T extends LabelHandler> T getLabelOfType(String label, Class<T> type) {
            var handler = labelHandlers.get(label);
            return type.isInstance(handler) ? (T) handler : null;
        }
    }

    public record BackportConfiguration(
            List<String> preApplyGenCommands,
            List<String> postApplyGenCommands,
            List<String> preApplyCommands,
            List<String> postApplyCommands,
            @Nullable String diffPattern
    ) {
        public static final BackportConfiguration DEFAULT = new BackportConfiguration(List.of(), List.of(), List.of(), List.of(), null);
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

    public RepoConfiguration getRepo(GHRepository repository) {
        return repositories().getOrDefault(repository.getFullName().toLowerCase(Locale.ROOT), RepoConfiguration.DEFAULT);
    }

    public static RepoConfiguration get(GHRepository repository) {
        return configuration.getRepo(repository);
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

    public record Commands(List<String> prefixes, boolean reactToComment, boolean minimizeComment) {}

    public record PRActions(String repository, String workflow) {}
}
