package mx.marjan.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Cursor;
import javafx.stage.Window;

/** Runs database work off the JavaFX Application Thread and hands the result back on it. */
public final class Async {

    private static int busy;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            runnable -> {
                Thread thread = new Thread(runnable, "sysport-worker");
                thread.setDaemon(true);
                return thread;
            });

    private Async() {}

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        busy(true);
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            try {
                onSuccess.accept(task.getValue());
            } finally {
                busy(false);
            }
        });
        task.setOnFailed(event -> {
            try {
                onError.accept(unwrap(task.getException()));
            } finally {
                busy(false);
            }
        });
        POOL.execute(task);
    }

    /** Shows a wait cursor on every open window while at least one worker is running. */
    private static void busy(boolean enter) {
        Runnable update = () -> {
            busy += enter ? 1 : -1;
            if (busy < 0) {
                busy = 0;
            }
            Cursor cursor = busy > 0 ? Cursor.WAIT : null;
            for (Window window : Window.getWindows()) {
                if (window.getScene() != null) {
                    window.getScene().setCursor(cursor);
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure != null && failure.getCause() != null) {
            return failure.getCause();
        }
        return failure == null ? new IllegalStateException("Unknown failure") : failure;
    }
}
