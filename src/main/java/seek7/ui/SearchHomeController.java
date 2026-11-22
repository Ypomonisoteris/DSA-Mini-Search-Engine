package seek7.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import seek7.service.SearchEngineService;
import seek7.service.SearchRequest;
import seek7.ui.components.IconFactory;

/** Controls the search experience and the existing workspace navigation. */
public final class SearchHomeController {
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;
    private static final int RESULT_PREVIEW_LIMIT = 5;

    @FXML private Button searchNavigation;
    @FXML private Button documentsNavigation;
    @FXML private Button historyNavigation;
    @FXML private Button settingsNavigation;
    @FXML private Button searchButton;
    @FXML private ToggleButton anyTermToggle;
    @FXML private ToggleButton allTermsToggle;
    @FXML private TextField searchInput;
    @FXML private Label pageName;
    @FXML private Label documentCount;
    @FXML private Label feedbackLabel;
    @FXML private Label resultSummary;
    @FXML private Label resultQuery;
    @FXML private Label recentSearchCount;
    @FXML private Label recentSearchTitle;
    @FXML private Label recentSearchDescription;
    @FXML private StackPane searchIcon;
    @FXML private StackPane folderIcon;
    @FXML private StackPane pageHost;
    @FXML private HBox searchFieldShell;
    @FXML private VBox searchPage;
    @FXML private VBox suggestionPanel;
    @FXML private VBox suggestionList;
    @FXML private VBox resultsPanel;
    @FXML private VBox resultsList;

    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seek7-search");
        thread.setDaemon(true);
        return thread;
    });

    private List<Button> navigationItems;
    private final List<Button> suggestionButtons = new ArrayList<>();
    private SearchEngineService searchEngineService;
    private SearchEngineService.AutocompleteResult autocompleteResult;
    private SearchEngineService.SearchResponse lastSearchResponse;
    private String lastSubmittedQuery;
    private SearchEngineService.Mode lastSubmittedMode;
    private int selectedSuggestionIndex = -1;
    private boolean autocompleteDismissed;
    private boolean suppressAutocompleteRefresh;
    private boolean searchInProgress;

    @FXML
    private void initialize() {
        searchNavigation.setGraphic(IconFactory.create("search", 16));
        documentsNavigation.setGraphic(IconFactory.create("documents", 16));
        historyNavigation.setGraphic(IconFactory.create("history", 16));
        settingsNavigation.setGraphic(IconFactory.create("settings", 16));
        searchIcon.getChildren().setAll(IconFactory.create("search", 24));
        folderIcon.getChildren().setAll(IconFactory.create("folder", 16));
        searchButton.setGraphic(IconFactory.create("search", 16));
        navigationItems = List.of(searchNavigation, documentsNavigation, historyNavigation, settingsNavigation);

        ToggleGroup modeGroup = new ToggleGroup();
        anyTermToggle.setToggleGroup(modeGroup);
        allTermsToggle.setToggleGroup(modeGroup);
        anyTermToggle.setSelected(true);

        searchInput.textProperty().addListener((observable, previous, current) -> {
            if (!suppressAutocompleteRefresh) {
                autocompleteDismissed = false;
                lastSearchResponse = null;
                hideResults();
                hideFeedback();
            }
            updateAutocomplete();
        });
        searchInput.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            searchFieldShell.getStyleClass().remove("focused");
            if (isFocused) {
                searchFieldShell.getStyleClass().add("focused");
                updateAutocomplete();
            } else {
                hideSuggestions();
            }
        });
        searchInput.setOnKeyPressed(this::handleSearchKeyPressed);
    }

    public void setSearchEngineService(SearchEngineService service) {
        searchEngineService = service;
        documentCount.setText(Integer.toString(service.documentCount()));
    }

    public String getLastSubmittedQuery() {
        return lastSubmittedQuery;
    }

    public SearchEngineService.Mode getLastSubmittedMode() {
        return lastSubmittedMode;
    }

    public SearchEngineService.SearchResponse getLastSearchResponse() {
        return lastSearchResponse;
    }

    public void close() {
        searchExecutor.shutdownNow();
    }

    @FXML
    private void showSearch() {
        setActiveNavigation(searchNavigation, "Search");
        pageHost.getChildren().setAll(searchPage);
    }

    @FXML
    private void showDocuments() {
        showPlaceholder(documentsNavigation, "Documents", "Your library", "Your documents are ready to explore.",
                "Document browsing will be added in a future SEEK7 update.");
    }

    @FXML
    private void showHistory() {
        showPlaceholder(historyNavigation, "Search History", "Search history", "Your searches stay local.",
                "A dedicated search history view will be added in a future SEEK7 update.");
    }

    @FXML
    private void showSettings() {
        showPlaceholder(settingsNavigation, "Settings", "Preferences", "Make this workspace yours.",
                "Workspace preferences will be available in a future SEEK7 update.");
    }

    @FXML
    private void submitSearch() {
        if (searchInProgress) {
            return;
        }
        if (searchEngineService == null) {
            showFeedback("The local search index is not available.", "feedback-error");
            return;
        }

        SearchEngineService.Mode selectedMode = allTermsToggle.isSelected()
                ? SearchEngineService.Mode.ALL_TERMS
                : SearchEngineService.Mode.ANY_TERM;
        SearchRequest parsed = SearchRequest.parse(searchInput.getText(), selectedMode);
        if (!parsed.isValid()) {
            autocompleteDismissed = true;
            hideSuggestions();
            showFeedback("Enter a word or phrase to search.", "feedback-error");
            searchInput.requestFocus();
            return;
        }

        autocompleteDismissed = true;
        suppressAutocompleteRefresh = true;
        searchInput.setText(parsed.query());
        searchInput.positionCaret(parsed.query().length());
        suppressAutocompleteRefresh = false;
        anyTermToggle.setSelected(parsed.mode() == SearchEngineService.Mode.ANY_TERM);
        allTermsToggle.setSelected(parsed.mode() == SearchEngineService.Mode.ALL_TERMS);
        hideSuggestions();
        hideFeedback();

        lastSubmittedQuery = parsed.query();
        lastSubmittedMode = parsed.mode();
        searchInProgress = true;
        setBusy(true);
        resultsList.getChildren().clear();
        resultsPanel.setVisible(false);
        resultsPanel.setManaged(false);
        showFeedback("Searching local documents...", "feedback-working");

        SearchEngineService service = searchEngineService;
        Task<SearchEngineService.SearchResponse> task = new Task<>() {
            @Override
            protected SearchEngineService.SearchResponse call() {
                return service.search(parsed.query(), parsed.mode(), RESULT_PREVIEW_LIMIT);
            }
        };
        task.setOnSucceeded(event -> {
            searchInProgress = false;
            setBusy(false);
            lastSearchResponse = task.getValue();
            hideFeedback();
            showResults(parsed.query(), parsed.mode(), lastSearchResponse);
            updateRecentSearch(parsed.query(), parsed.mode());
        });
        task.setOnFailed(event -> {
            searchInProgress = false;
            setBusy(false);
            resultsPanel.setVisible(false);
            resultsPanel.setManaged(false);
            showFeedback("Search could not be completed. Please try again.", "feedback-error");
        });
        searchExecutor.execute(task);
    }

    private void handleSearchKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.DOWN && suggestionPanel.isVisible()) {
            moveSuggestionSelection(1);
            event.consume();
        } else if (event.getCode() == KeyCode.UP && suggestionPanel.isVisible()) {
            moveSuggestionSelection(-1);
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE && suggestionPanel.isVisible()) {
            autocompleteDismissed = true;
            hideSuggestions();
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER
                && suggestionPanel.isVisible()
                && selectedSuggestionIndex >= 0) {
            selectSuggestion(selectedSuggestionIndex);
            event.consume();
        }
    }

    private void updateAutocomplete() {
        String query = searchInput.getText();
        if (searchEngineService == null
                || query == null
                || query.isEmpty()
                || !searchInput.isFocused()
                || autocompleteDismissed
                || suppressAutocompleteRefresh
                || searchInProgress) {
            hideSuggestions();
            return;
        }

        autocompleteResult = searchEngineService.autocomplete(query, MAX_VISIBLE_SUGGESTIONS);
        if (autocompleteResult.suggestions().isEmpty()) {
            hideSuggestions();
            return;
        }

        selectedSuggestionIndex = -1;
        suggestionButtons.clear();
        suggestionList.getChildren().clear();
        for (int i = 0; i < autocompleteResult.suggestions().size(); i++) {
            String suggestion = autocompleteResult.suggestions().get(i);
            int index = i;
            Button item = new Button(suggestion);
            item.setGraphic(IconFactory.create("search", 16));
            item.setMaxWidth(Double.MAX_VALUE);
            item.setFocusTraversable(false);
            item.getStyleClass().add("suggestion-item");
            item.setOnMouseEntered(event -> setSelectedSuggestion(index));
            item.setOnAction(event -> selectSuggestion(index));
            suggestionButtons.add(item);
            suggestionList.getChildren().add(item);
        }
        suggestionPanel.setManaged(true);
        suggestionPanel.setVisible(true);
    }

    private void moveSuggestionSelection(int movement) {
        if (suggestionButtons.isEmpty()) {
            return;
        }
        if (selectedSuggestionIndex < 0) {
            setSelectedSuggestion(movement > 0 ? 0 : suggestionButtons.size() - 1);
            return;
        }
        setSelectedSuggestion(Math.max(0, Math.min(suggestionButtons.size() - 1,
                selectedSuggestionIndex + movement)));
    }

    private void setSelectedSuggestion(int index) {
        if (index < 0 || index >= suggestionButtons.size()) {
            return;
        }
        for (Button button : suggestionButtons) {
            button.getStyleClass().remove("selected");
        }
        suggestionButtons.get(index).getStyleClass().add("selected");
        selectedSuggestionIndex = index;
    }

    private void selectSuggestion(int index) {
        if (autocompleteResult == null
                || index < 0
                || index >= autocompleteResult.suggestions().size()) {
            return;
        }

        String suggestion = autocompleteResult.suggestions().get(index);
        String query = searchInput.getText();
        int replacementStart = Math.max(0, Math.min(autocompleteResult.replacementStart(), query.length()));
        String completedQuery = query.substring(0, replacementStart) + suggestion + " ";

        autocompleteDismissed = true;
        suppressAutocompleteRefresh = true;
        searchInput.setText(completedQuery);
        searchInput.positionCaret(completedQuery.length());
        suppressAutocompleteRefresh = false;
        hideSuggestions();
        searchInput.requestFocus();
    }

    private void showResults(String query, SearchEngineService.Mode mode,
                             SearchEngineService.SearchResponse response) {
        resultQuery.setText(query);
        String modeLabel = mode == SearchEngineService.Mode.ALL_TERMS ? "All terms" : "Any term";
        resultSummary.setText(response.totalMatches() + " matching "
                + (response.totalMatches() == 1 ? "document" : "documents") + " · " + modeLabel);
        resultsList.getChildren().clear();

        for (SearchEngineService.SpellingHint hint : response.spellingHints()) {
            Label spelling = new Label("Did you mean “" + hint.suggestion() + "” instead of “" + hint.typedTerm() + "”? ");
            spelling.getStyleClass().add("spelling-hint");
            spelling.setWrapText(true);
            resultsList.getChildren().add(spelling);
        }

        if (response.hits().isEmpty()) {
            Label emptyMessage = new Label("No matching documents were found.");
            emptyMessage.getStyleClass().add("result-empty-message");
            resultsList.getChildren().add(emptyMessage);
        } else {
            for (SearchEngineService.SearchHit hit : response.hits()) {
                resultsList.getChildren().add(createResultRow(hit));
            }
        }

        resultsPanel.setManaged(true);
        resultsPanel.setVisible(true);
    }

    private VBox createResultRow(SearchEngineService.SearchHit hit) {
        Label name = new Label("[" + hit.documentId() + "]  " + hit.fileName());
        name.getStyleClass().add("result-name");
        Label score = new Label(String.format(Locale.ROOT, "%.4f", hit.score()));
        score.getStyleClass().add("result-score");
        HBox heading = new HBox(12, name, score);
        heading.getStyleClass().add("result-heading");

        Label snippet = new Label(hit.snippet());
        snippet.getStyleClass().add("result-snippet");
        snippet.setWrapText(true);

        VBox row = new VBox(8, heading, snippet);
        row.getStyleClass().add("search-result-row");
        return row;
    }

    private void updateRecentSearch(String query, SearchEngineService.Mode mode) {
        String modeLabel = mode == SearchEngineService.Mode.ALL_TERMS ? "All terms" : "Any term";
        recentSearchCount.setText("1");
        recentSearchTitle.setText(query);
        recentSearchDescription.setText("Latest " + modeLabel + " query in this session.");
    }

    private void setBusy(boolean busy) {
        searchInput.setDisable(busy);
        anyTermToggle.setDisable(busy);
        allTermsToggle.setDisable(busy);
        searchButton.setDisable(busy);
        searchButton.setText(busy ? "Searching" : "Search");
        searchButton.setGraphic(busy ? null : IconFactory.create("search", 16));
    }

    private void showFeedback(String message, String styleClass) {
        feedbackLabel.getStyleClass().removeAll("feedback-error", "feedback-working");
        feedbackLabel.getStyleClass().add(styleClass);
        feedbackLabel.setText(message);
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }

    private void hideSuggestions() {
        suggestionPanel.setVisible(false);
        suggestionPanel.setManaged(false);
        selectedSuggestionIndex = -1;
        suggestionButtons.clear();
        suggestionList.getChildren().clear();
    }

    private void hideResults() {
        resultsPanel.setVisible(false);
        resultsPanel.setManaged(false);
    }

    private void showPlaceholder(Button selected, String section, String eyebrow, String heading, String message) {
        setActiveNavigation(selected, section);

        VBox content = new VBox(24);
        content.getStyleClass().add("placeholder-page");
        content.setPadding(new Insets(64, 64, 64, 64));
        content.setMaxWidth(920);

        Label sectionLabel = new Label(eyebrow.toUpperCase(Locale.ROOT));
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
