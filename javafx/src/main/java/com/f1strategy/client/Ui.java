package com.f1strategy.client;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Small builders shared by all views: cards, stat boxes, headings, rows.
 * Keeping them here is what keeps the views readable (and the theme consistent).
 */
final class Ui {

    static final String STYLE_CARD = "card";
    static final String STYLE_PRIMARY = "primary";

    private Ui() {
    }

    static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    /** Colored compound chip using the real F1 tyre language (S red / M yellow / H white). */
    static Label compoundChip(String compound) {
        Label chip = label(compound, "chip", "chip-" + compound);
        chip.setMinWidth(52);
        chip.setAlignment(javafx.geometry.Pos.CENTER);
        return chip;
    }

    static Button button(String text) {
        return new Button(text);
    }

    static Button button(String text, boolean primary) {
        Button b = new Button(text);
        if (primary) {
            b.getStyleClass().add(STYLE_PRIMARY);
        }
        return b;
    }

    static HBox row(Node... children) {
        HBox box = new HBox(10, children);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return box;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** A titled card containing {@code content}, used for every panel in the app. */
    static VBox card(String title, Node... content) {
        VBox box = new VBox(10);
        box.getStyleClass().add(STYLE_CARD);
        if (title != null) {
            box.getChildren().add(label(title, "section-title"));
        }
        box.getChildren().addAll(content);
        return box;
    }

    /** A small statistic box: big value over a grey caption. */
    static VBox stat(String caption, String initial) {
        VBox box = new VBox(4);
        box.getStyleClass().add(STYLE_CARD);
        Label value = label(initial, "stat-value");
        value.setId(caption.replace(' ', '-') + "-value");
        box.getChildren().addAll(value, label(caption, "stat-label"));
        return box;
    }

    static VBox padded(Pane pane, double inset) {
        pane.setPadding(new Insets(inset));
        return (VBox) pane;
    }
}
