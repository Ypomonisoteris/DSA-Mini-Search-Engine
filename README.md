# SEEK7

SEEK7 is a local-file mini search engine built as a Data Structures and Algorithms project. Version **1.1.0** introduces the foundation for a dark, professional JavaFX desktop application while keeping the original interactive command-line search engine available.

The desktop screen establishes SEEK7 branding, navigation, a Search home view, a local document count, and clearly marked placeholders for search and autocomplete. Search, document browsing, and settings are not connected to the desktop interface yet; this version does not generate search results or sample history.

## Requirements

- JDK 25 or later
- Maven 3.9 or later
- Visual Studio Code with the Extension Pack for Java (for VS Code development)

JavaFX dependencies are resolved by Maven for the current operating system. No separate JavaFX SDK is needed.

## Run the desktop application

Open the repository folder in VS Code, allow the Java extension to import the Maven project, then run the `SEEK7: Run desktop app` task from **Terminal → Run Task**.

From a terminal in the repository root, the same command is:

```bash
mvn javafx:run
```

To compile and package the application without launching it:

```bash
mvn clean package
```

The initial window opens at 1440 × 900 and can be resized (minimum 1000 × 700). The document count is read from regular `.txt` files directly inside `documents/`; if that folder is absent, the desktop screen reports zero files and does not create sample data.

### VS Code setup

1. Install JDK 25 or later and Maven 3.9 or later.
2. Set `JAVA_HOME` to the JDK installation and ensure Maven is on `PATH`.
3. Open this repository folder in VS Code and install the recommended Extension Pack for Java.
4. Let VS Code import the Maven dependencies, then choose **Terminal → Run Task → SEEK7: Run desktop app**.

The included `.vscode` settings and tasks point the Java tooling at `JAVA_HOME` and provide build and run shortcuts. The Maven `javafx:run` goal is also available from the Maven view.

## Run the original command-line search engine

The original implementation remains at the repository root and is separate from the JavaFX source tree. It can still be run from the repository root with Java 11 or later:

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

The CLI indexes `.txt` files in `documents/` and creates its 50 sample documents when that folder is missing or contains no text files. Its search implementation and behavior are unchanged by the desktop foundation.

## Desktop structure

```text
MiniSearchEngine.java                 Existing command-line search engine
documents/                            Sample plain-text search collection
src/main/java/seek7/
  Seek7Application.java               JavaFX entry point and resizable stage
  service/DocumentLibrarySummary.java Counts local text documents
  ui/SearchHomeController.java         Home view and visual navigation states
  ui/components/IconFactory.java       Reusable vector icons
src/main/resources/seek7/
  ui/search-home.fxml                  Application layout
  styles/theme.css                     Dark SEEK7 design system
.vscode/                               Java tooling settings and run/build tasks
pom.xml                                Java 25 and JavaFX Maven configuration
```

## Design foundation

The initial palette follows the project brief: deep navy backgrounds, slate surfaces and borders, blue accents, and high-contrast text. CSS keeps the colors, typography, spacing, control states, and surface treatment separate from Java code. Layout spacing follows a 4px increment system, with 8px to 12px rounded surfaces. Inter is used for interface text and JetBrains Mono for compact technical labels when those fonts are installed; the JavaFX/system fallback is used otherwise.

Sidebar destinations switch between the Search home and small, explicitly marked placeholder screens. The search field is intentionally non-editable in this version. Recent searches start empty, and the library count reflects files found on disk.

## License

This project is distributed under the MIT License. See [LICENSE](LICENSE).
