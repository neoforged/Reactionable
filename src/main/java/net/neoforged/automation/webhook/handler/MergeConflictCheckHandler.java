package net.neoforged.automation.webhook.handler;

import com.github.api.GetPullRequestQuery;
import com.github.api.GetPullRequestsQuery;
import com.github.api.fragment.PullRequestInfo;
import com.github.api.type.MergeableState;
import com.github.api.type.PullRequestState;
import net.neoforged.automation.util.GHAction;
import net.neoforged.automation.util.Label;
import net.neoforged.automation.webhook.impl.GitHubEvent;
import net.neoforged.automation.webhook.impl.MultiEventHandler;
import net.neoforged.automation.webhook.impl.WebhookHandler;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MergeConflictCheckHandler implements MultiEventHandler {
    private static final long PR_BASE_TIME = 3;
    private static final ScheduledThreadPoolExecutor SERVICE = new ScheduledThreadPoolExecutor(1);
    private static final Set<String> IN_PROGRESS_REPOS = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void register(WebhookHandler handler) {
        handler.registerHandler(GitHubEvent.PUSH, (gitHub, payload) -> {
            // Only run once for a repository
            if (!IN_PROGRESS_REPOS.add(payload.getRepository().getFullName())) {
                return;
            }

            var ref = payload.getRef();
            if (!ref.startsWith("refs/heads/")) return; // Only check for commits pushed to branches
            var branch = ref.substring(11);
            checkPRConflicts(gitHub, payload.getRepository(), branch);
        });

        handler.registerFilteredHandler(GitHubEvent.PULL_REQUEST, (gitHub, payload, action) -> {
            while (checkConflict(gitHub, GitHubAccessor.graphQl(gitHub, GetPullRequestQuery.builder()
                            .owner(payload.getRepository().getOwnerName())
                            .name(payload.getRepository().getName())
                            .number(payload.getPullRequest().getNumber())
                            .build())
                    .repository()
                    .pullRequest().fragments().pullRequestInfo()) == MergeableState.UNKNOWN) {
                Thread.sleep(PR_BASE_TIME * 1000);
            }
        }, GHAction.SYNCHRONIZE);
    }

    public static void checkPRConflicts(GitHub gitHub, GHRepository repository, String branchName) throws IOException {
        // TODO - paginate
        final var prs = GitHubAccessor.graphQl(gitHub, GetPullRequestsQuery.builder()
                        .owner(repository.getOwnerName())
                        .name(repository.getName())
                        .baseRef(branchName)
                        .states(List.of(PullRequestState.OPEN))
                        .build()).repository().pullRequests()
                .nodes();

        final long unknownAmount = prs.stream().filter(it -> it.fragments().pullRequestInfo().mergeable() == MergeableState.UNKNOWN).count();
        if (unknownAmount > 0) {
            // If we don't know the status of one or more PRs, give GitHub some time to think.
            SERVICE.schedule(() -> {
                try {
                    checkPRConflicts(gitHub, repository, branchName);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }, PR_BASE_TIME * unknownAmount, TimeUnit.SECONDS);
            return;
        }

        for (final var node : prs) {
            checkConflict(gitHub, node.fragments().pullRequestInfo());
        }

        IN_PROGRESS_REPOS.remove(repository.getFullName());
    }

    public static MergeableState checkConflict(GitHub gitHub, PullRequestInfo info) throws IOException {
        final boolean hasLabel = info.labels().nodes().stream().anyMatch(node -> node.name().equalsIgnoreCase(Label.NEEDS_REBASE.getLabelName()));
        final MergeableState state = info.mergeable();
        if (hasLabel && state == MergeableState.CONFLICTING) return state; // We have conflicts and the PR has the label already

        final int number = info.number();
        final GHRepository repo = gitHub.getRepository(info.repository().nameWithOwner());
        final GHPullRequest pr = repo.getPullRequest(number);
        if (hasLabel && state == MergeableState.MERGEABLE) {
            // We don't have conflicts but the PR has the label... remove it.
            Label.NEEDS_REBASE.remove(pr);

            final List<GHUser> toRequest = new ArrayList<>();
            for (final GHPullRequestReview review : pr.listReviews()) {
                if (review.getUser().isMemberOf(gitHub.getOrganization(repo.getOwnerName())) && review.getState() == GHPullRequestReviewState.APPROVED) {
                    toRequest.add(review.getUser());
                }
            }
            if (!toRequest.isEmpty()) pr.requestReviewers(toRequest);
        } else if (state == MergeableState.CONFLICTING && !Label.NEEDS_REBASE.has(pr)) {
            // We have conflicts but the PR doesn't have the label... add it.
            Label.NEEDS_REBASE.label(pr);

            pr.comment("@%s, this pull request has conflicts, please resolve them for this PR to move forward.".formatted(pr.getUser().getLogin()));
        }

        return state;
    }
}
