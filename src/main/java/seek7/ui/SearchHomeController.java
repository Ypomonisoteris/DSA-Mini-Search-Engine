package seek7.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import seek7.service.SearchEngineService;
import seek7.service.SearchRequest;
import seek7.ui.components.IconFactory;

/** Controls search, results, and document reading views. */
public final class SearchHomeController {
    private static final int MAX_VISIBLE_SUGGESTIONS = 8;

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
    @FXML private Label recentSearchCount;
    @FXML private Label recentSearchTitle;
    @FXML private Label recentSearchDescription;
    @FXML private StackPane searchIcon;
    @FXML private StackPane folderIcon;
    @FXML private StackPane pageHost;
    @FXML private HBox searchFieldShell;
    @FXML private ScrollPane contentScroll;
    @FXML private VBox searchPage;
    @FXML private VBox heroSection;
    @FXML private VBox searchBlock;
    @FXML private VBox suggestionPanel;
    @FXML private VBox suggestionList;

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
    private VBox resultsPage;
    private VBox documentPage;
    private double resultsScrollPosition;
    private int selectedSuggestionIndex = -1;
    private boolean autocompleteDismissed;
    private boolean suppressAutocompleteRefresh;
    private boolean searchInProgress;
    private Task<Optional<SearchEngineService.IndexedDocument>> activeDocumentTask;

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
        if (activeDocumentTask != null) {
            activeDocumentTask.cancel(true);
        }
        searchExecutor.shutdownNow();
    }

    @FXML
    private void showSearch() {
        setActiveNavigation(searchNavigation, "Search");
        moveSearchBlockTo(heroSection);
        pageHost.getChildren().setAll(searchPage);
        contentScroll.setVvalue(0.0);
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

        SearchEngineService.Mode selectedMode = selectedMode();
        SearchRequest parsed = SearchRequest.parse(searchInput.getText(), selectedMode);
        if (!parsed.isValid()) {
            autocompleteDismissed = true;
            hideSuggestions();
            showFeedback("Enter a word or phrase to search.", "feedback-error");
            searchInput.requestFocus();
            return;
        }

        autocompleteDismissed = true;
        setSearchInput(parsed.query());
        setSelectedMode(parsed.mode());
        hideSuggestions();
        hideFeedback();

        searchInProgress = true;
        setBusy(true);
        showFeedback("Searching local documents...", "feedback-working");

        SearchEngineService service = searchEngineService;
        Task<SearchEngineService.SearchResponse> task = new Task<>() {
            @Override
            protected SearchEngineService.SearchResponse call() {
                return service.search(parsed.query(), parsed.mode(), Integer.MAX_VALUE);
            }
        };
        task.setOnSucceeded(event -> {
            searchInProgress = false;
            setBusy(false);
            lastSearchResponse = task.getValue();
            lastSubmittedQuery = parsed.query();
            lastSubmittedMode = parsed.mode();
            hideFeedback();
            showResultsPage(parsed.query(), parsed.mode(), lastSearchResponse);
            updateRecentSearch(parsed.query(), parsed.mode());
        });
        task.setOnFailed(event -> {
            searchInProgress = false;
            setBusy(false);
            showFeedback("Search could not be completed. Please try again.", "feedback-error");
        });
        searchExecutor.execute(task);
    }

    private SearchEngineService.Mode selectedMode() {
        return allTermsToggle.isSelected()
                ? SearchEngineService.Mode.ALL_TERMS
                : SearchEngineService.Mode.ANY_TERM;
    }

    private void setSelectedMode(SearchEngineService.Mode mode) {
        anyTermToggle.setSelected(mode == SearchEngineService.Mode.ANY_TERM);
        allTermsToggle.setSelected(mode == SearchEngineService.Mode.ALL_TERMS);
    }

    private void setSearchInput(String query) {
        suppressAutocompleteRefresh = true;
        searchInput.setText(query);
        searchInput.positionCaret(query.length());
        suppressAutocompleteRefresh = false;
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
        setSearchInput(completedQuery);
        hideSuggestions();
        searchInput.requestFocus();
    }

    private void showResultsPage(String query, SearchEngineService.Mode mode,
                                 SearchEngineService.SearchResponse response) {
        if (resultsPage == null) {
            resultsPage = new VBox(22);
            resultsPage.getStyleClass().add("results-page");
            resultsPage.setMaxWidth(1040);
        }
        resultsPage.getChildren().clear();
        moveSearchBlockTo(resultsPage);

        Button backToSearch = new Button("Back to search");
        backToSearch.getStyleClass().add("back-button");
        backToSearch.setOnAction(event -> showSearch());

        String countText = resultCountText(response);
        Label count = new Label(countText);
        count.getStyleClass().add("count-pill");
        HBox topRow = new HBox(12, backToSearch, flexibleSpace(), count);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = new Label("SEARCH RESULTS");
        eyebrow.getStyleClass().add("eyebrow");
        Label heading = new Label("Results for \"" + query + "\"");
        heading.getStyleClass().add("page-heading");
        heading.setWrapText(true);
        Label summary = new Label(resultSummaryText(response, mode));
        summary.getStyleClass().add("page-description");
        VBox pageHeader = new VBox(8, eyebrow, heading, summary);
        pageHeader.getStyleClass().add("results-page-heading");

        VBox suggestions = createSpellingSuggestions(query, mode, response.spellingHints());
        VBox cards = new VBox(12);
        cards.getStyleClass().add("result-card-list");
        if (response.hits().isEmpty()) {
            cards.getChildren().add(createEmptyResultsState(query));
        } else {
            for (int i = 0; i < response.hits().size(); i++) {
                cards.getChildren().add(createResultCard(i + 1, response.hits().get(i), query));
            }
        }

        resultsPage.getChildren().addAll(topRow, pageHeader, searchBlock, suggestions, cards);
        hideSuggestions();
        setActiveNavigation(searchNavigation, "Results");
        pageHost.getChildren().setAll(resultsPage);
        contentScroll.setVvalue(0.0);
    }

    private static String resultCountText(SearchEngineService.SearchResponse response) {
        int returned = response.hits().size();
        if (response.totalMatches() > returned) {
            return returned + " shown / " + response.totalMatches() + " matches";
        }
        return returned + (returned == 1 ? " result returned" : " results returned");
    }

    private static String resultSummaryText(SearchEngineService.SearchResponse response,
                                            SearchEngineService.Mode mode) {
        String modeLabel = mode == SearchEngineService.Mode.ALL_TERMS ? "All terms" : "Any term";
        if (response.totalMatches() > response.hits().size()) {
            return "Showing " + response.hits().size() + " of " + response.totalMatches()
                    + " matching documents; the engine limited this result set. " + modeLabel + " mode.";
        }
        return response.hits().size() + (response.hits().size() == 1
                ? " document returned" : " documents returned") + " in " + modeLabel + " mode.";
    }

    private VBox createSpellingSuggestions(String query, SearchEngineService.Mode mode,
                                           List<SearchEngineService.SpellingHint> hints) {
        VBox section = new VBox(8);
        section.getStyleClass().add("spelling-suggestions");
        for (SearchEngineService.SpellingHint hint : hints) {
            Label lead = new Label("Did you mean");
            Button replacement = new Button("\"" + hint.suggestion() + "\"");
            replacement.getStyleClass().add("spelling-suggestion-button");
            replacement.setOnAction(event -> searchWithSpellingSuggestion(query, mode, hint));
            Label context = new Label("instead of \"" + hint.typedTerm() + "\"?");
            HBox row = new HBox(5, lead, replacement, context);
            row.setAlignment(Pos.CENTER_LEFT);
            section.getChildren().add(row);
        }
        section.setVisible(!hints.isEmpty());
        section.setManaged(!hints.isEmpty());
        return section;
    }

    private void searchWithSpellingSuggestion(String query, SearchEngineService.Mode mode,
                                              SearchEngineService.SpellingHint hint) {
        String expression = "(?i)(?<![A-Za-z0-9])" + Pattern.quote(hint.typedTerm())
                + "(?![A-Za-z0-9])";
        String correctedQuery = Pattern.compile(expression)
                .matcher(query)
                .replaceAll(Matcher.quoteReplacement(hint.suggestion()));
        setSearchInput(correctedQuery);
        setSelectedMode(mode);
        submitSearch();
    }

    private VBox createEmptyResultsState(String query) {
        Label title = new Label("No results for \"" + query + "\"");
        title.getStyleClass().add("empty-title");
        Label copy = new Label("Try different keywords or remove a term from your query.");
        copy.getStyleClass().add("empty-copy");
        copy.setWrapText(true);
        VBox empty = new VBox(10, title, copy);
        empty.getStyleClass().add("results-empty-state");
        return empty;
    }

    private Button createResultCard(int rank, SearchEngineService.SearchHit hit, String query) {
        Label ranking = new Label(String.format(Locale.ROOT, "%02d", rank));
        ranking.getStyleClass().add("result-ranking");

        Label fileName = new Label(hit.fileName());
        fileName.getStyleClass().add("result-file-name");
        fileName.setWrapText(true);
        Label documentId = new Label("Document ID " + hit.documentId());
        documentId.getStyleClass().add("result-document-id");
        VBox documentInfo = new VBox(5, fileName, documentId);
        documentInfo.getStyleClass().add("result-document-info");
        HBox.setHgrow(documentInfo, javafx.scene.layout.Priority.ALWAYS);

        Label scoreCaption = new Label("RELEVANCE SCORE");
        scoreCaption.getStyleClass().add("result-score-caption");
        Label score = new Label(String.format(Locale.ROOT, "%.6f", hit.score()));
        score.getStyleClass().add("result-score");
        VBox scoreInfo = new VBox(5, scoreCaption, score);
        scoreInfo.setAlignment(Pos.CENTER_RIGHT);

        HBox top = new HBox(14, ranking, documentInfo, scoreInfo);
        top.setAlignment(Pos.CENTER_LEFT);
        TextFlow snippet = createHighlightedSnippet(hit.snippet(), query);
        snippet.getStyleClass().add("result-snippet-flow");

        VBox content = new VBox(14, top, snippet);
        content.setFillWidth(true);
        Button card = new Button();
        card.setGraphic(content);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAccessibleRole(AccessibleRole.BUTTON);
        card.setAccessibleText("Open document " + hit.fileName() + ", document ID " + hit.documentId());
        card.getStyleClass().add("result-card");
        card.setOnAction(event -> openDocument(hit));
        return card;
    }

    private TextFlow createHighlightedSnippet(String snippet, String query) {
        TextFlow flow = new TextFlow();
        flow.setMaxWidth(Double.MAX_VALUE);
        List<String> terms = new LinkedHashSet<>(List.of(query.toLowerCase(Locale.ROOT)
                .split("[^a-z0-9]+"))).stream()
                .filter(term -> !term.isBlank())
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .toList();
        if (terms.isEmpty()) {
            addSnippetText(flow, snippet, false);
            return flow;
        }

        String alternatives = terms.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9])(?:" + alternatives + ")(?![A-Za-z0-9])");
        Matcher matcher = pattern.matcher(snippet);
        int cursor = 0;
        while (matcher.find()) {
            addSnippetText(flow, snippet.substring(cursor, matcher.start()), false);
            addSnippetText(flow, matcher.group(), true);
            cursor = matcher.end();
        }
        addSnippetText(flow, snippet.substring(cursor), false);
        return flow;
    }

    private static void addSnippetText(TextFlow flow, String value, boolean match) {
        if (value.isEmpty()) {
            return;
        }
        Text text = new Text(value);
        text.getStyleClass().add(match ? "snippet-match" : "snippet-copy");
        flow.getChildren().add(text);
    }

    private void openDocument(SearchEngineService.SearchHit hit) {
        if (searchEngineService == null || activeDocumentTask != null) {
            return;
        }

        resultsScrollPosition = contentScroll.getVvalue();
        setActiveNavigation(searchNavigation, "Document");
        documentPage = createDocumentLoadingPage(hit);
        pageHost.getChildren().setAll(documentPage);
        contentScroll.setVvalue(0.0);

        SearchEngineService service = searchEngineService;
        Task<Optional<SearchEngineService.IndexedDocument>> task = new Task<>() {
            @Override
            protected Optional<SearchEngineService.IndexedDocument> call() {
                return service.documentById(hit.documentId());
            }
        };
        activeDocumentTask = task;
        task.setOnSucceeded(event -> {
            if (activeDocumentTask != task) {
                return;
            }
            activeDocumentTask = null;
            Optional<SearchEngineService.IndexedDocument> document = task.getValue();
            documentPage = document.map(value -> createDocumentPage(value))
                    .orElseGet(() -> createDocumentUnavailablePage(hit,
                            "This document ID is not available in the current index."));
            pageHost.getChildren().setAll(documentPage);
            contentScroll.setVvalue(0.0);
        });
        task.setOnFailed(event -> {
            if (activeDocumentTask != task) {
                return;
            }
            activeDocumentTask = null;
            documentPage = createDocumentUnavailablePage(hit,
                    "The document could not be loaded. Return to results and try another document.");
            pageHost.getChildren().setAll(documentPage);
            contentScroll.setVvalue(0.0);
        });
        searchExecutor.execute(task);
    }

    private VBox createDocumentLoadingPage(SearchEngineService.SearchHit hit) {
        VBox page = createDocumentPageShell(hit.fileName(), hit.documentId());
        Label loading = new Label("Loading document...");
        loading.getStyleClass().add("viewer-loading");
        VBox content = new VBox(14, loading);
        content.getStyleClass().add("document-reading-card");
        page.getChildren().add(content);
        return page;
    }

    private VBox createDocumentPage(SearchEngineService.IndexedDocument document) {
        VBox page = createDocumentPageShell(document.fileName(), document.documentId());
        TextArea content = new TextArea(document.content());
        content.setEditable(false);
        content.setWrapText(true);
        content.setPrefRowCount(30);
        content.setPrefHeight(600);
        content.setMaxWidth(Double.MAX_VALUE);
        content.getStyleClass().add("document-content");
        VBox readingCard = new VBox(content);
        readingCard.getStyleClass().add("document-reading-card");
        page.getChildren().add(readingCard);
        return page;
    }

    private VBox createDocumentUnavailablePage(SearchEngineService.SearchHit hit, String message) {
        VBox page = createDocumentPageShell(hit.fileName(), hit.documentId());
        Label title = new Label("Document unavailable");
        title.getStyleClass().add("empty-title");
        Label copy = new Label(message);
        copy.getStyleClass().add("empty-copy");
        copy.setWrapText(true);
        VBox error = new VBox(10, title, copy);
        error.getStyleClass().add("results-empty-state");
        page.getChildren().add(error);
        return page;
    }

    private VBox createDocumentPageShell(String fileName, int documentId) {
        VBox page = new VBox(22);
        page.getStyleClass().add("document-page");
        page.setMaxWidth(1040);

        Button back = new Button("Back to results");
        back.getStyleClass().add("back-button");
        back.setOnAction(event -> returnToResults());
        Label eyebrow = new Label("DOCUMENT VIEWER");
        eyebrow.getStyleClass().add("eyebrow");
        Label title = new Label(fileName);
        title.getStyleClass().add("page-heading");
        title.setWrapText(true);
        Label id = new Label("Document ID " + documentId);
        id.getStyleClass().add("viewer-document-id");
        VBox heading = new VBox(8, eyebrow, title, id);
        page.getChildren().addAll(back, heading);
        return page;
    }

    private void returnToResults() {
        if (activeDocumentTask != null) {
            activeDocumentTask.cancel(true);
            activeDocumentTask = null;
        }
        if (resultsPage == null) {
            showSearch();
            return;
        }
        setActiveNavigation(searchNavigation, "Results");
        pageHost.getChildren().setAll(resultsPage);
        Platform.runLater(() -> contentScroll.setVvalue(resultsScrollPosition));
    }

    private void moveSearchBlockTo(VBox destination) {
        if (searchBlock.getParent() instanceof Pane parent) {
            parent.getChildren().remove(searchBlock);
        }
        if (!destination.getChildren().contains(searchBlock)) {
            destination.getChildren().add(searchBlock);
        }
    }

    private static Region flexibleSpace() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        return spacer;
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
