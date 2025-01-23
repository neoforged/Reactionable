package org.kohsuke.github;

import com.apollographql.apollo.api.Adapter;
import com.apollographql.apollo.api.CustomScalarAdapters;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Operations;
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter;
import com.apollographql.apollo.api.json.BufferedSourceJsonReader;
import com.apollographql.apollo.api.json.JsonReader;
import com.apollographql.apollo.api.json.JsonWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import net.neoforged.automation.util.AuthUtil;
import okio.Buffer;
import okio.Okio;
import org.apache.commons.io.input.ReaderInputStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.function.InputStreamFunction;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GitHubAccessor {

    public static final CustomScalarAdapters ADAPTERS = new CustomScalarAdapters.Builder()
            .add(com.github.api.type.URI.type, new Adapter<URI>() {
                @Override
                public void toJson(@NotNull JsonWriter jsonWriter, @NotNull CustomScalarAdapters customScalarAdapters, URI uri) throws IOException {
                    jsonWriter.value(uri.toString());
                }

                @Override
                public URI fromJson(@NotNull JsonReader jsonReader, @NotNull CustomScalarAdapters customScalarAdapters) throws IOException {
                    return URI.create(jsonReader.nextString());
                }
            })
            .build();

    public static ObjectReader objectReader(GitHub gitHub) {
        return GitHubClient.getMappingObjectReader(gitHub);
    }

    public static JsonNode graphQl(GitHub gitHub, String query, Object... args) throws IOException {
        return gitHub.createRequest()
                .method("POST")
                .inBody()
                .with("query", query.formatted(args))
                .withUrlPath("/graphql")
                .fetch(JsonNode.class);
    }

    public static <T> T graphQl(GitHub gitHub, String query, InputStreamFunction<T> isF) throws IOException {
        return gitHub.createRequest()
                .method("POST")
                .inBody()
                .with(new ReaderInputStream(new StringReader(query), StandardCharsets.UTF_8))
                .withUrlPath("/graphql")
                .fetchStream(isF);
    }

    @NotNull
    public static <T extends Operation.Data> T graphQl(GitHub gitHub, Operation<T> call) throws IOException {
        var buf = new Buffer();
        var writer = new BufferedSinkJsonWriter(buf);
        Operations.composeJsonRequest(call, writer, ADAPTERS);
        var query = buf.readUtf8();
        return graphQl(gitHub, query, in -> {
            var reader = new BufferedSourceJsonReader(Okio.buffer(Okio.source(in)));
            return Operations.parseResponse(call, reader, null, ADAPTERS).data;
        });
    }

    public static void lock(GHIssue issue, @Nullable LockReason reason) throws IOException {
        if (reason == null) {
            issue.lock();
        } else {
            issue.root().createRequest().method("PUT").withUrlPath(issue.getIssuesApiRoute() + "/lock")
                    .inBody().with("lock_reason", reason.toString()).send();
        }
    }

    public static void merge(GHPullRequest pr, String title, String message, GHPullRequest.MergeMethod method) throws IOException {
        pr.root().createRequest()
                .method("PUT")
                .with("commit_message", message == null ? "" : message)
                .with("commit_title", title)
                .with("sha", pr.getHead().getSha())
                .with("merge_method", method)
                .withUrlPath(pr.getApiRoute() + "/merge")
                .send();
    }

    public static GHUser getAuthor(GHPullRequest pr) {
        return pr.user;
    }

    public static String getToken(GitHub gitHub) throws IOException {
        return gitHub.getClient().getEncodedAuthorization().replace("Bearer ", "");
    }

    public static PagedIterable<GHCache> getCaches(GHRepository repository) throws IOException {
        var req = repository.root().createRequest()
                .method("GET")
                .withUrlPath(repository.getApiTailUrl("/actions/caches"));
        return new GHCachesIterable(repository, req);
    }

    public static IssueEdit edit(GHIssue issue) {
        final Requester request = issue.root().createRequest().method("PATCH")
                .inBody().withUrlPath(issue.getApiRoute());
        return new IssueEdit() {
            @Override
            public IssueEdit edit(String key, Object value) {
                request.with(key, value);
                return this;
            }

            @Override
            public void send() throws IOException {
                request.send();
            }
        };
    }

    public static PersonIdent ident(GHUser githubUser) {
        var userEmail = githubUser.getId() + "+" + githubUser.getLogin() + "@users.noreply.github.com";
        return new PersonIdent(githubUser.getLogin(), userEmail);
    }

    public interface IssueEdit {
        IssueEdit edit(String key, Object value);

        void send() throws IOException;
    }

    public static <T extends GHEventPayload> T parseEventPayload(GitHub gitHub, byte[] payload, Class<T> type) throws IOException {
        T t = GitHubClient.getMappingObjectReader(gitHub).forType(type).readValue(payload);
        t.lateBind();
        return t;
    }

    private static final Map<GHRepository, Set<String>> EXISTING_LABELS = new ConcurrentHashMap<>();
    public static Set<String> getExistingLabels(GHRepository repository) throws IOException {
        Set<String> ex = EXISTING_LABELS.get(repository);
        if (ex != null) return ex;
        ex = repository.listLabels().toList().stream().map(GHLabel::getName).collect(Collectors.toSet());
        EXISTING_LABELS.put(repository, ex);
        return ex;
    }

    public static GHApp getApp(GitHub owner) throws IOException {
        try {
            var field = GitHubClient.class.getDeclaredField("authorizationProvider");
            field.setAccessible(true);
            return ((AuthUtil.AppBasedAuthProvider) field.get(owner.getClient())).getApp().getApp();
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
