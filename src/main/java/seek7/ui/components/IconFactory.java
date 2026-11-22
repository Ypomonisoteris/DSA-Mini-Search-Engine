package seek7.ui.components;

import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Builds the small, consistent line icons used by the navigation and search field. */
public final class IconFactory {
    private static final Map<String, String> PATHS = Map.of(
            "search", "M15.4 15.4L20 20M17.2 10.2C17.2 14.07 14.07 17.2 10.2 17.2C6.33 17.2 3.2 14.07 3.2 10.2C3.2 6.33 6.33 3.2 10.2 3.2C14.07 3.2 17.2 6.33 17.2 10.2Z",
            "documents", "M6 3.5H14.5L18.5 7.5V20.5H6V3.5ZM14.5 3.5V7.5H18.5M9 12H15.5M9 16H15.5",
            "history", "M3.3 11.3A8.7 8.7 0 1 0 5.8 5.5L3.5 7.8M3.5 3.5V7.8H7.8M12 7V12L15.5 14",
            "settings", "M4 6H20M4 12H20M4 18H20M9 6A2 2 0 1 1 5 6A2 2 0 1 1 9 6ZM18 12A2 2 0 1 1 14 12A2 2 0 1 1 18 12ZM12 18A2 2 0 1 1 8 18A2 2 0 1 1 12 18Z",
            "folder", "M3.5 7.5H9L11 9.5H20.5V19.5H3.5V7.5ZM3.5 7.5V5.5H9L11 7.5");

    private IconFactory() {
    }

    public static Node create(String name, double size) {
        String path = PATHS.get(name);
        if (path == null) {
            throw new IllegalArgumentException("Unknown icon: " + name);
        }

        SVGPath drawing = new SVGPath();
        drawing.setContent(path);
        drawing.getStyleClass().add("vector-icon");
        drawing.setScaleX(size / 24.0);
        drawing.setScaleY(size / 24.0);

        StackPane icon = new StackPane(drawing);
        icon.getStyleClass().add("icon-frame");
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        icon.setMouseTransparent(true);
        return icon;
    }
}
