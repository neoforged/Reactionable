package net.neoforged.automation.service;

import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.neoforged.automation.util.Util;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RenovateMetadataService {
    private static final String BASE_MAVEN_URL = "https://maven.neoforged.net/releases/";
    private static final String BASE_API_URL = "https://maven.neoforged.net/api/maven/versions/releases/";
    private static final Pattern VERSION_CHANGELOG_PATTERN = Pattern.compile(" - `([\\da-z-.]+)` (.+)");

    public static void get(Context context) throws Exception {
        var pkg = context.pathParam("package");
        var current = context.pathParam("current");
        try {
            var meta = provideMetadata(pkg, current);
            if (meta == null) {
                context.status(HttpStatus.NOT_FOUND);
            } else {
                context.json(meta)
                        .status(HttpStatus.OK);
            }
        } catch (IOException ignored) {
            context.status(HttpStatus.NOT_FOUND);
        }
    }

    public static Object provideMetadata(String packageName, String current) throws Exception {
        var since = new DefaultArtifactVersion(current);

        var splitName = packageName.split(":");
        var name = splitName[1];
        var baseUrl = splitName[0].replace(".", "/") + "/" + name;

        record VersionList(boolean isSnapshot, List<String> versions) {}
        var versions = Util.readAs(URI.create(BASE_API_URL + baseUrl), VersionList.class)
                .versions().stream()
                .filter(s -> new DefaultArtifactVersion(s).compareTo(since) >= 0)
                .collect(Collectors.toMap(Function.identity(), ver -> {
                    var map = HashMap.<String, String>newHashMap(3);
                    map.put("version", ver);
                    map.put("changelogUrl", BASE_MAVEN_URL + baseUrl +
                            "/" + ver + "/" + name  + "-" + ver + "-changelog.txt");
                    return map;
                }, (a, b) -> b, LinkedHashMap::new));

        String homepage = null;
        String sourceUrl = null;
        String ghRepo = null;

        var latest = versions.lastEntry() == null ? null : versions.lastEntry().getKey();
        if (latest != null) {
            var meta = URI.create(
                    BASE_MAVEN_URL + baseUrl +
                            "/" + latest + "/" + name  + "-" + latest + ".pom"
            );
            try (var is = meta.toURL().openStream()) {
                var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
                var xpath = XPathFactory.newInstance().newXPath();
                homepage = xpath.compile("/project/url/text()").evaluate(doc);

                sourceUrl = xpath.compile("/project/scm/url/text()").evaluate(doc);
                if (sourceUrl.startsWith("https://github.com/")) {
                    ghRepo = sourceUrl.replace("https://github.com/", "")
                            .replaceAll("/$", "");
                }
            } catch (IOException ignored) {

            }
        }

        var pendingChangelogs = new LinkedHashSet<>(versions.keySet());

        while (!pendingChangelogs.isEmpty()) {
            var lastVersion = pendingChangelogs.removeLast();
            var changelogUri = URI.create(
                    BASE_MAVEN_URL + baseUrl +
                            "/" + lastVersion + "/" + name  + "-" + lastVersion + "-changelog.txt"
            );
            try (var is = changelogUri.toURL().openStream()) {
                var text = new String(is.readAllBytes(), StandardCharsets.UTF_8).split("\n");
                for (int i = 0; i < text.length; i++) {
                    var line = text[i];
                    if (line.startsWith(" -")) {
                        var matcher = VERSION_CHANGELOG_PATTERN.matcher(line);
                        if (matcher.find()) {
                            var version = matcher.group(1);
                            StringBuilder changelog = new StringBuilder(matcher.group(2));
                            while (i < text.length - 1 && text[i + 1].startsWith("   ")) {
                                var nextLine = text[++i].substring(3);
                                if (nextLine.isEmpty()) {
                                    changelog.append('\n');
                                } else {
                                    changelog.append(nextLine);
                                }
                            }

                            pendingChangelogs.remove(version);
                            var ver = versions.get(version);
                            if (ver != null) {
                                if (ghRepo == null) {
                                    ver.put("changelogContent", changelog.toString());
                                } else {
                                    ver.put("changelogContent", replaceGitHubReferences(changelog.toString(), ghRepo));
                                }
                            }
                        }
                    }
                }
            } catch (IOException ignored) {
                // Maybe the changelog doesn't exist
            }
        }

        var result = new HashMap<String, Object>();
        if (homepage != null) result.put("homepage", homepage);
        if (sourceUrl != null) result.put("sourceUrl", sourceUrl);

        result.put("packageScope", splitName[0]);
        result.put("releases", versions.values());

        return result;
    }

    public static String replaceGitHubReferences(String changelog, String repo) {
        return changelog.replaceAll("\\(#(?<number>\\d+)\\)", "[(#$1)](https://github.com/" + repo + "/pull/$1)")
                .replaceAll("(?m)^ - ", "- ")
                .replaceAll("(?mi)(?<type>(?:close|fix|resolve)(?:s|d|es|ed)?) #(?<number>\\d+)", "$1 [#$2](https://github.com/" + repo + "/issues/$2)");
    }
}
