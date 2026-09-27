package mx.marjan.ui;

import javafx.scene.control.Label;

/** A small colored chip used to show a status consistently across screens. */
public final class StatusBadge {

    public enum Tone { NEUTRAL, INFO, SUCCESS, WARNING, DANGER }

    private StatusBadge() {}

    public static Label of(String text, Tone tone) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().addAll("badge", "badge-" + tone.name().toLowerCase());
        return label;
    }
}
