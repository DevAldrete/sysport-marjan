package mx.marjan.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import mx.marjan.security.Session;
import org.kordamp.ikonli.feather.Feather;

/**
 * The left sidebar: navigation entries grouped by area, filtered by the current
 * user's permissions. It can collapse to icons only.
 */
public final class Navigation extends VBox {

    /** One navigable screen. A {@code null} permission is always visible. */
    public record Item(String group, String title, Feather icon, String permission,
            Supplier<Node> screen) {}

    private final ToggleGroup group = new ToggleGroup();
    private final List<ToggleButton> buttons = new ArrayList<>();
    private final List<Label> groupLabels = new ArrayList<>();
    private final Map<ToggleButton, Item> items = new LinkedHashMap<>();
    private final VBox links = new VBox(2);
    private Consumer<Item> onSelect = item -> {};
    private boolean collapsed;

    public Navigation(List<Item> all) {
        getStyleClass().add("sidebar");
        VBox brand = new VBox(2);
        brand.getStyleClass().add("brand");
        Label title = new Label("SysPort");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Transportes MARJAN");
        subtitle.getStyleClass().add("brand-subtitle");
        brand.getChildren().addAll(title, subtitle);
        groupLabels.add((Label) subtitle);

        Map<String, List<Item>> byGroup = new LinkedHashMap<>();
        for (Item item : all) {
            if (item.permission() != null && !Session.has(item.permission())) {
                continue;
            }
            byGroup.computeIfAbsent(item.group(), key -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<Item>> entry : byGroup.entrySet()) {
            Label groupLabel = new Label(entry.getKey().toUpperCase());
            groupLabel.getStyleClass().add("nav-group-label");
            groupLabels.add(groupLabel);
            links.getChildren().add(groupLabel);
            for (Item item : entry.getValue()) {
                links.getChildren().add(button(item));
            }
        }

        ScrollPane scroll = new ScrollPane(links);
        scroll.getStyleClass().add("nav-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(brand, scroll);
    }

    private ToggleButton button(Item item) {
        ToggleButton button = new ToggleButton(item.title(), Icons.icon(item.icon(), 16));
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setTooltip(new Tooltip(item.title()));
        button.setOnAction(event -> {
            if (!button.isSelected()) {
                button.setSelected(true);
            }
            onSelect.accept(item);
        });
        buttons.add(button);
        items.put(button, item);
        return button;
    }

    public void setOnSelect(Consumer<Item> handler) {
        this.onSelect = handler;
    }

    /** Marks the given item as the active one. */
    public void select(Item item) {
        items.forEach((button, candidate) -> button.setSelected(candidate.equals(item)));
    }

    public Item first() {
        return items.values().stream().findFirst().orElse(null);
    }

    /** The visible item with the given title, or null when the user lacks permission. */
    public Item byTitle(String title) {
        return items.values().stream()
                .filter(item -> item.title().equals(title))
                .findFirst().orElse(null);
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        getStyleClass().remove("sidebar-collapsed");
        if (collapsed) {
            getStyleClass().add("sidebar-collapsed");
        }
        for (Label label : groupLabels) {
            label.setVisible(!collapsed);
            label.setManaged(!collapsed);
        }
        items.forEach((button, item) -> button.setText(collapsed ? "" : item.title()));
    }

    public boolean isCollapsed() {
        return collapsed;
    }
}
