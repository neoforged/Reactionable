package net.neoforged.automation.runner;

public interface RunnerAction {
    void run(ActionRunner runner) throws Exception;

    default RunnerAction andThen(RunnerAction other) {
        return runner -> {
            this.run(runner);
            other.run(runner);
        };
    }
}
