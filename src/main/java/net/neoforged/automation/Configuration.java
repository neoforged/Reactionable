package net.neoforged.automation;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import net.neoforged.automation.util.Util;
import net.neoforged.automation.webhook.label.LabelHandler;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Configuration(
        Commands commands,
        PRActions prActions,
        Map<String, RepoConfiguration> repositories
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepoConfiguration(Boolean enabled,
                                    @JsonDeserialize(contentUsing = LabelHandlerConfig.Deserializer.class) Map<String, LabelHandlerConfig> labelHandlers,
                                    @JsonDeserialize(using = NullDeser.class) List<Map.Entry<Pattern, LabelHandlerConfig>> regexLabelHandlers,
                                    @Nullable String baseRunCommand, BackportConfiguration backport) {
        public RepoConfiguration {
            enabled = enabled == null || enabled;
            labelHandlers = labelHandlers == null ? Map.of() : labelHandlers;
            backport = backport == null ? BackportConfiguration.DEFAULT : backport;

            regexLabelHandlers = labelHandlers.entrySet().stream()
                    .filter(e -> e.getValue().regex())
                    .map(e -> Map.entry(Pattern.compile(e.getKey()), e.getValue()))
                    .toList();
        }
        public static final RepoConfiguration DEFAULT = new RepoConfiguration(true, Map.of(), List.of(), null, BackportConfiguration.DEFAULT);

        @Nullable
        @SuppressWarnings("unchecked")
        public <T extends LabelHandler> T getLabelOfType(String label, Class<T> type) {
            var cfg = getLabelHandler(label);
            return type.isInstance(cfg) ? (T) cfg : null;
        }

        @Nullable
        public LabelHandler getLabelHandler(String label) {
            var handlerConfig = labelHandlers.get(label);
            if (handlerConfig != null && !handlerConfig.regex()) return handlerConfig.labelCreator().apply(null);
            for (var entry : regexLabelHandlers) {
                var matcher = entry.getKey().matcher(label);
                if (matcher.matches()) {
                    return entry.getValue().labelCreator().apply(matcher);
                }
            }
            return null;
        }

        public record LabelHandlerConfig(boolean regex, Function<Matcher, LabelHandler> labelCreator) {
            private static final class Deserializer extends JsonDeserializer<LabelHandlerConfig> {
                @Override
                public LabelHandlerConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                    var node = (ObjectNode) p.readValueAs(JsonNode.class);
                    var isRegex = node.has("regex") && node.get("regex").asBoolean(false);
                    node.remove("regex");

                    var type = LabelHandler.TYPES.get(node.get("type").asText());
                    node.remove("type");

                    if (isRegex) {

                        return new LabelHandlerConfig(true, k -> {
                            var newNode = recreateSubstituting(node, k);
                            try {
                                return ctxt.readTreeAsValue(newNode, type);
                            } catch (IOException e) {
                                Util.sneakyThrow(e);
                                return null;
                            }
                        });
                    } else {
                        var handler = ctxt.readTreeAsValue(node, type);
                        return new LabelHandlerConfig(false, k -> handler);
                    }
                }

                private static final Pattern LITERAL_PATTERN = Pattern.compile("(^|[^\\\\])\\$\\{(\\w+)}");

                private static JsonNode recreateSubstituting(JsonNode node, Matcher matcher) {
                    return switch (node) {
                        case TextNode txt ->
                                new TextNode(LITERAL_PATTERN.matcher(txt.textValue()).replaceAll(res -> res.group(1) + matcher.group(res.group(2))));
                        case ArrayNode ar ->
                                new ArrayNode(MAPPER.getNodeFactory(), StreamSupport.stream(ar.spliterator(), false)
                                    .map(n -> recreateSubstituting(n, matcher))
                                    .toList());
                        case ObjectNode o -> {
                            var newO = MAPPER.createObjectNode();
                            o.fields().forEachRemaining(field -> newO.putIfAbsent(field.getKey(), recreateSubstituting(field.getValue(), matcher)));
                            yield newO;
                        }

                        default -> node;
                    };
                }
            }
        }

        static final class NullDeser extends JsonDeserializer<Object> {

            @Override
            public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
                return null;
            }
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
