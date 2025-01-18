package org.kohsuke.github;

import java.io.IOException;

public class GHCache extends GitHubInteractiveObject {
    private String key;

    private GHRepository repository;

    public String getKey() {
        return key;
    }

    public void delete() throws IOException {
         root().createRequest().method("DELETE")
                .withUrlPath(repository.getApiTailUrl("/actions/caches"))
                .with("key", key)
                .send();
    }

    void wrapUp(GHRepository repository) {
        this.repository = repository;
    }

    @Override
    public String toString() {
        return "GHCache{key=" + key + "}";
    }
}
