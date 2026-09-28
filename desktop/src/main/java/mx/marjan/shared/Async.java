package mx.marjan.shared;

import java.awt.Cursor;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Runs database work off the Event Dispatch Thread and hands the result back on it. */
public final class Async {

    private static int busy;

    private Async() {}

    public static <T> void run(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        busy(true);
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
                } finally {
                    busy(false);
                }
            }
        }.execute();
    }

    /** Shows a wait cursor on every window while at least one worker is running. */
    private static synchronized void busy(boolean enter) {
        busy += enter ? 1 : -1;
        if (busy < 0) {
            busy = 0;
        }
        Cursor cursor = busy > 0 ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null;
        for (Window window : Window.getWindows()) {
            window.setCursor(cursor);
        }
    }
}
