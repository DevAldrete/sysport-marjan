package mx.marjan.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/** Runs database work off the JavaFX Application Thread and hands the result back on it. */
public final class Async {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            runnable -> {
                Thread thread = new Thread(runnable, "sysport-worker");
                thread.setDaemon(true);
                return thread;
            });

    private Async() {}

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(unwrap(task.getException())));
        POOL.execute(task);
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure != null && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure == null ? new IllegalStateException("Unknown failure") : failure;
    }
}
