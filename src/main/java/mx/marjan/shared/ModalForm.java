package mx.marjan.shared;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.concurrent.Callable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Standard form dialog: fields, Save/Cancel, and consistent problem display.
 * The submit handler runs off the EDT and only closes the dialog on success.
 */
public final class ModalForm {

    private ModalForm() {}

    public static void show(Component parent, String title, FormPanel form,
            Callable<Result<?>> onSubmit) {
        show(parent, title, form, onSubmit, null);
    }

    public static void show(Component parent, String title, FormPanel form,
            Callable<Result<?>> onSubmit, Runnable afterSave) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        content.add(form.panel(), BorderLayout.CENTER);
        dialog.add(content, BorderLayout.CENTER);

        JButton save = new JButton("Guardar");
        JButton cancel = new JButton("Cancelar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(save);
        dialog.add(buttons, BorderLayout.SOUTH);

        cancel.addActionListener(event -> dialog.dispose());
        save.addActionListener(event -> {
            save.setEnabled(false);
            Async.run(onSubmit,
                    result -> {
                        if (result.isErr()) {
                            save.setEnabled(true);
                            Ui.error(dialog, "Validacion", result.problems());
                        } else {
                            dialog.dispose();
                            if (afterSave != null) {
                                afterSave.run();
                            }
                        }
                    },
                    failure -> {
                        save.setEnabled(true);
                        Ui.failure(dialog, failure);
                    });
        });

        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}
