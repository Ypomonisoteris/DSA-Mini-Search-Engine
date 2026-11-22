package seek7.ui;

import java.util.List;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import seek7.ui.components.IconFactory;

/** Controls visual navigation and factual library metadata for the foundation screen. */
public final class SearchHomeController {
    @FXML private Button searchNavigation;
    @FXML private Button documentsNavigation;
    @FXML private Button historyNavigation;
    @FXML private Button settingsNavigation;
    @FXML private Label pageName;
    @FXML private Label documentCount;
    @FXML private StackPane searchIcon;
    @FXML private StackPane folderIcon;
    @FXML private StackPane pageHost;
    @FXML private VBox searchPage;

    private List<Button> navigationItems;

    @FXML
    private void initialize() {
        searchNavigation.setGraphic(IconFactory.create("search", 16));
        documentsNavigation.setGraphic(IconFactory.create("documents", 16));
        historyNavigation.setGraphic(IconFactory.create("history", 16));
        settingsNavigation.setGraphic(IconFactory.create("settings", 16));
        searchIcon.getChildren().setAll(IconFactory.create("search", 24));
        folderIcon.getChildren().setAll(IconFactory.create("folder", 16));
        navigationItems = List.of(searchNavigation, documentsNavigation, historyNavigation, settingsNavigation);
    }

    public void setDocumentCount(long count) {
        documentCount.setText(Long.toString(count));
    }

    @FXML
    private void showSearch() {
        setActiveNavigation(searchNavigation, "Search");
        pageHost.getChildren().setAll(searchPage);
    }

    @FXML
    private void showDocuments() {
        showPlaceholder(documentsNavigation, "Documents", "Your library", "Your documents are ready to explore.",
                "Document browsing will connect to the local index in a future SEEK7 update.");
    }

    @FXML
    private void showHistory() {
        showPlaceholder(historyNavigation, "Search History", "Search history", "No search activity yet.",
                "Recent searches will appear here once desktop search is connected.");
    }

    @FXML
    private void showSettings() {
        showPlaceholder(settingsNavigation, "Settings", "Preferences", "Make this workspace yours.",
                "Workspace preferences will be available in a future SEEK7 update.");
    }

    private void showPlaceholder(Button selected, String section, String eyebrow, String heading, String message) {
        setActiveNavigation(selected, section);

        VBox content = new VBox(24);
        content.getStyleClass().add("placeholder-page");
        content.setPadding(new Insets(64, 64, 64, 64));
        content.setMaxWidth(920);

        Label sectionLabel = new Label(eyebrow.toUpperCase());
        sectionLabel.getStyleClass().add("eyebrow");

        Label title = new Label(heading);
        title.getStyleClass().add("page-heading");
        title.setWrapText(true);

        Label description = new Label(message);
        description.getStyleClass().add("page-description");
        description.setWrapText(true);
        description.setMaxWidth(560);

        Label noteTitle = new Label("DESIGN FOUNDATION");
        Label noteCopy = new Label("This view is a visual shell for a later version. No application data is stored here.");
        noteCopy.setWrapText(true);
        VBox note = new VBox(12, noteTitle, noteCopy);
        note.getStyleClass().add("placeholder-note");
        noteTitle.getStyleClass().add("placeholder-note-title");
        noteCopy.getStyleClass().add("placeholder-note-copy");
        note.setMaxWidth(620);

        content.getChildren().addAll(sectionLabel, title, description, note);
        pageHost.getChildren().setAll(content);
    }

    private void setActiveNavigation(Button selected, String section) {
        for (Button item : navigationItems) {
            item.getStyleClass().remove("active");
        }
        selected.getStyleClass().add("active");
        pageName.setText(section);
    }
}
