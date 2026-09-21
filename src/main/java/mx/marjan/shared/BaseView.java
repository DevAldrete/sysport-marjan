package mx.marjan.shared;

import java.awt.BorderLayout;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/**
 * Common screen shell: a border layout with padding, plus helpers to load data
 * on a background thread and refresh the view. Views only display and collect input.
 */
public abstract class BaseView extends JPanel {

    protected BaseView() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    /** Reloads the view's data from the database (implemented by each screen). */
    public abstract void reload();

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess) {
        Async.run(task, onSuccess, failure -> Ui.failure(this, failure));
    }

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Async.run(task, onSuccess, onError);
    }
}
