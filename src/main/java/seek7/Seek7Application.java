package seek7;

import java.io.IOException;
import java.nio.file.Path;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import seek7.service.DocumentLibrarySummary;
import seek7.ui.SearchHomeController;

/** JavaFX entry point for the SEEK7 desktop foundation. */
public final class Seek7Application extends Application {
    private static final double INITIAL_WIDTH = 1440;
    private static final double INITIAL_HEIGHT = 900;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Seek7Application.class.getResource("/seek7/ui/search-home.fxml"));
        Parent root = loader.load();

        SearchHomeController controller = loader.getController();
        controller.setDocumentCount(DocumentLibrarySummary.countTextDocuments(Path.of("documents")));

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(
                Seek7Application.class.getResource("/seek7/styles/theme.css").toExternalForm());

        stage.setTitle("SEEK7 — Local Search");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
