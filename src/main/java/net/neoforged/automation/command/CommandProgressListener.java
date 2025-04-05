package net.neoforged.automation.command;

import net.neoforged.automation.command.api.GHCommandContext;
import net.neoforged.automation.runner.ActionRunner;
import net.neoforged.automation.util.Util;
import org.kohsuke.github.GHIssueComment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CommandProgressListener {
    private final GHCommandContext context;
    private final String header;

    private final List<String> steps = new ArrayList<>();

    private final GHIssueComment comment;

    public CommandProgressListener(GHCommandContext context, String header) throws IOException {
        this.context = context;
        this.header = header;

        comment = context.issue().comment(header);
    }

    public synchronized void addStep(String step) {
        steps.add(step);

        var message = new StringBuilder(header).append("\n");
        message.append("<details>\n\n<summary>Click for step information</summary>\n\n");

        message.append(String.join("\n", steps));

        message.append("\n\n</details>");
        try {
            comment.update(message.toString());
        } catch (IOException e) {
            Util.sneakyThrow(e);
        }
    }

    public void handleFailure(ActionRunner runner, String err) {
        context.onError().run();
        try {
            var message = new StringBuilder();
            message.append("@").append(context.user().getLogin()).append(" failure executing command `")
                    .append(context.command()).append("`.\n\n");

            message.append("<details>\n\n<summary>Click for failure reason</summary>\n\n");

            message.append(err).append("\n").append(runner.getRun(context.gitHub()).getHtmlUrl()).append("\n\n</details>");

            context.issue().comment(message.toString());
        } catch (Exception ex) {
            Util.sneakyThrow(ex);
        }
    }
}
