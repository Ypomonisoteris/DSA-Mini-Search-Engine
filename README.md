# SEEK7

SEEK7 is a local-file mini search engine built as a Data Structures and Algorithms project. Version **1.3.0** adds a complete ranked results page and a document reader to the JavaFX application while preserving the original command-line search engine.

## Requirements

- JDK 25 or later
- Maven 3.9 or later
- Visual Studio Code with the Extension Pack for Java (for VS Code development)

Maven resolves the JavaFX dependencies and native libraries for the current operating system. No separate JavaFX SDK is needed.

## Run the desktop application

Open the repository folder in VS Code, allow the Java extension to import the Maven project, then run the `SEEK7: Run desktop app` task from **Terminal > Run Task**.

From a terminal in the repository root, run:

```bash
mvn javafx:run
```

Compile the application:

```bash
mvn clean compile
```

Run the search service integration tests:

```bash
mvn test
```

The app indexes regular `.txt` files directly inside `documents/`. If the folder is absent, it starts with an empty index and does not create sample data. The window opens at 1440 x 900 and can be resized (minimum 1000 x 700).

### VS Code setup

1. Install JDK 25 or later and Maven 3.9 or later.
2. Set `JAVA_HOME` to the JDK installation and ensure Maven is on `PATH`.
3. Open this repository folder in VS Code and install the recommended Extension Pack for Java.
4. Let VS Code import the Maven dependencies, then choose **Terminal > Run Task > SEEK7: Run desktop app**.

The included `.vscode` settings and tasks point Java tooling at `JAVA_HOME` and provide build and run shortcuts. The Maven `javafx:run` goal is also available from the Maven view.

## Search and results

- Type a query and press Enter or click **Search**. Search execution runs outside the JavaFX application thread.
- Choose **Any term** to match at least one query word or **All terms** to require every word. The `all:` query prefix selects All terms automatically.
- As you type a word prefix, up to eight indexed Trie terms appear. Use Up/Down to highlight one, Enter to insert it, click to select it, and Escape to dismiss suggestions.
- The results page preserves the submitted query and selected mode. It displays every hit returned by the current engine in its original ranking order, with the engine's document ID, filename, snippet, and TF-IDF-style relevance score.
- Scores are shown as numeric ranking values with six decimal places. They are not percentages or probabilities.
- Matching query terms are highlighted for reading. This affects only the snippet presentation; the snippet itself comes from the existing engine.
- The result count describes the results returned. If a service response is capped, the UI reports how many matches were returned and how many matched in total.
- Engine-provided spelling suggestions appear only when available. Select a suggested term to run a corrected query in the same search mode.
- No-result searches display the submitted query and a prompt to try different keywords.

## Document viewer

Select a result card to open that indexed document. The viewer displays its original filename, document ID, and complete text as loaded into the existing index. Text line breaks are retained, and long files can be scrolled in the reading pane.

Use **Back to results** to return to the previous results without searching again. The submitted query, selected mode, results, and results-page scroll position are retained. Document retrieval runs outside the JavaFX application thread; unavailable or invalid IDs produce an in-app error state.

## Run the original command-line search engine

The original implementation remains unchanged at the repository root. It can still be run from the repository root with Java 11 or later:

```bash
java MiniSearchEngine.java
```

Run its scripted example with:

```bash
java MiniSearchEngine.java --demo
```

Or compile and run it explicitly:

```bash
javac MiniSearchEngine.java
java MiniSearchEngine
```

The CLI indexes `.txt` files in `documents/` and creates its 50 sample documents when that folder is missing or contains no text files. Its search implementation, commands, and source-launch workflow remain unchanged.

## Repository structure

```text
MiniSearchEngine.java                       Original CLI search engine and algorithms
documents/                                  Local text document collection
src/main/java/seek7/Seek7Application.java   JavaFX application entry point
src/main/java/seek7/service/                Search service API and query model
src/main/java/seek7/ui/                     Search, results, viewer, and icon controls
src/main/java/MiniSearchEngineServiceProvider.java
                                             Adapter for the default-package engine
src/main/resources/seek7/ui/search-home.fxml Main application shell and home view
src/main/resources/seek7/styles/theme.css    SEEK7 design system and control states
src/main/resources/META-INF/services/        Search service provider registration
src/test/java/seek7/service/                 Search and document integration tests
.vscode/                                     Java tooling settings and tasks
pom.xml                                      Maven and JavaFX configuration
```

## Search engine integration

`MiniSearchEngine.java` remains in the default package so its CLI command continues to work. Maven copies that source into a generated source folder at build time. The default-package `MiniSearchEngineServiceProvider` calls the existing engine and exposes ranked hits, engine-generated snippets and spelling hints, autocomplete suggestions, and full indexed document text through `SearchEngineService`. The JavaFX controller does not duplicate the search, ranking, autocomplete, or spelling algorithms.

The application retains the SEEK7 dark palette, sidebar, navigation, and shared search controls. Results and document pages extend the same theme through JavaFX CSS.

## License

This project is distributed under the MIT License. See [LICENSE](LICENSE).
