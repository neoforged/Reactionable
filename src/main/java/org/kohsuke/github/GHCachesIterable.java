package org.kohsuke.github;

import java.util.Iterator;

class GHCachesIterable extends PagedIterable<GHCache> {
    private final transient GHRepository owner;
    private final GitHubRequest request;

    private Page result;

    public GHCachesIterable(GHRepository owner, GitHubRequest.Builder<?> requestBuilder) {
        this.owner = owner;
        this.request = requestBuilder.build();
    }

    @Override
    public PagedIterator<GHCache> _iterator(int pageSize) {
        return new PagedIterator<>(
                adapt(GitHubPageIterator.create(owner.root().getClient(), Page.class, request, pageSize)),
                null);
    }

    protected Iterator<GHCache[]> adapt(final Iterator<Page> base) {
        return new Iterator<>() {
            public boolean hasNext() {
                return base.hasNext();
            }

            public GHCache[] next() {
                Page v = base.next();
                if (result == null) {
                    result = v;
                }
                return v.getCaches(owner);
            }
        };
    }

    static class Page {
        private GHCache[] actions_caches;

        GHCache[] getCaches(GHRepository owner) {
            for (var cache : actions_caches) {
                cache.wrapUp(owner);
            }
            return actions_caches;
        }
    }
}
