package mx.marjan.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A screen shown for features that have not been ported to JavaFX yet. */
public final class PlaceholderScreen extends BaseView {

    private final String name;

    public PlaceholderScreen(String name) {
        this.name = name;
        Label title = new Label(name);
        title.getStyleClass().add("placeholder-title");
        Label text = new Label("Esta seccion se esta migrando a la nueva interfaz.");
        text.getStyleClass().add("placeholder-text");
        VBox box = new VBox(6, title, text);
        box.setAlignment(Pos.CENTER);
        setCenter(box);
    }

    @Override
    public void reload() {
        // Nothing to load yet.
    }
}
