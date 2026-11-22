# SEEK7

SEEK7 is a local-file mini search engine built as a Data Structures and Algorithms project. Version **1.2.0** adds a functional JavaFX search experience to the dark professional desktop foundation, while preserving the original interactive command-line program.

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

The initial window opens at 1440 x 900 and can be resized (minimum 1000 x 700). The app indexes regular `.txt` files directly inside `documents/`. If the folder is absent, it starts with an empty index and does not create sample data.

### VS Code setup

1. Install JDK 25 or later and Maven 3.9 or later.
2. Set `JAVA_HOME` to the JDK installation and ensure Maven is on `PATH`.
3. Open this repository folder in VS Code and install the recommended Extension Pack for Java.
4. Let VS Code import the Maven dependencies, then choose **Terminal > Run Task > SEEK7: Run desktop app**.

The included `.vscode` settings and tasks point Java tooling at `JAVA_HOME` and provide build and run shortcuts. The Maven `javafx:run` goal is also available from the Maven view.

## Search in the desktop app

- Type one or more words and press Enter or click **Search**.
- Choose **Any term** to match at least one query word or **All terms** to require every word. The UI also accepts the CLI-style `all:` prefix and selects All terms automatically.
- As you type a word prefix, up to eight indexed Trie terms appear. Use Up/Down to highlight one, Enter to insert it, click to select it, and Escape to dismiss suggestions.
- The query is trimmed and repeated whitespace is collapsed before search. Empty queries are rejected with an inline message.
- Search runs off the JavaFX thread. A compact preview shows up to five ranked matches and any spelling hints returned by the existing engine.

The UI does not include the full Version 1.3.0 results interface, persistent search history, document browsing, or settings yet.

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
MiniSearchEngine.java                       Original CLI search engine
documents/                                  Local sample document collection
src/main/java/seek7/Seek7Application.java   JavaFX application entry point
src/main/java/seek7/service/                Service API and query model
src/main/java/seek7/ui/                     Home controller and reusable icons
src/main/java/MiniSearchEngineServiceProvider.java
                                             Adapter for the default-package engine
src/main/resources/seek7/ui/search-home.fxml JavaFX layout
src/main/resources/seek7/styles/theme.css    SEEK7 design system and control states
src/main/resources/META-INF/services/        Search service provider registration
src/test/java/seek7/service/                 Search and Trie integration tests
.vscode/                                     Java tooling settings and tasks
pom.xml                                      Maven and JavaFX configuration
```

## Search engine integration

`MiniSearchEngine.java` remains in the default package so its CLI command continues to work. Maven copies that source into a generated source folder at build time. A `ServiceLoader` provider in the default package calls its existing `Engine`, `Trie`, scoring, and snippet methods and exposes them through the named-package `SearchEngineService` interface. The JavaFX controller contains no search, ranking, or autocomplete algorithms.

The existing palette, typography, sidebar, and layout are preserved. The search field, mode toggles, autocomplete dropdown, feedback, and compact result preview use the same surfaces, borders, and accent colors defined in `theme.css`.

## License

This project is distributed under the MIT License. See [LICENSE](LICENSE).
