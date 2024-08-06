package net.neoforged.automation.util;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHubAccessor;

import java.io.IOException;

public enum Label {
    NEEDS_REBASE("needs rebase", "This Pull Request needs to be rebased before being merged", "7F00FF")

    ;

    private final String name, description, colour;

    Label(String name, String description, String colour) {
        this.name = name;
        this.description = description;
        this.colour = colour;
    }

    public void label(GHIssue issue) throws IOException {
        if (!GitHubAccessor.getExistingLabels(issue.getRepository()).contains(name)) {
            create(issue.getRepository());
        }
        if (!has(issue)) {
            issue.addLabels(name);
        }
    }

    public void remove(GHIssue issue) throws IOException {
        if (has(issue)) {
            issue.removeLabel(name);
        }
    }

    public boolean has(GHIssue issue) {
        return issue.getLabels().stream().anyMatch(l -> l.getName().equals(name));
    }

    public void create(GHRepository repository) throws IOException {
        repository.createLabel(name, colour, description);
    }

    public String getLabelName() {
        return name;
    }
}
