package net.neoforged.automation.runner;

@FunctionalInterface
public interface ActionExceptionHandler {
    void accept(ActionRunner runner, String exception) throws Exception;

    default ActionExceptionHandler andThen(ActionExceptionHandler other) {
        return (runner, exception) -> {
            this.accept(runner, exception);
            other.accept(runner, exception);
        };
    }
}
