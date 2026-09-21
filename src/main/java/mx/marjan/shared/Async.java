package mx.marjan.shared;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Runs database work off the Event Dispatch Thread and hands the result back on it. */
public final class Async {

    private Async() {}

    public static <T> void run(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                    onError.accept(cause);
                }
            }
        }.execute();
    }
}
