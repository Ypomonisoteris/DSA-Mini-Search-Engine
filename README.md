# Mini Search Engine

A small, local-file search engine written in Java for a data structures and algorithms project. It indexes plain-text documents, searches them from an interactive command line, and ranks matches with a TF-IDF-style score. It does not crawl the web or use AI.

## Requirements

- Java 11 or later
- No third-party dependencies

## Run

From the repository root, start the interactive search prompt with Java's source-file launcher:

```bash
java MiniSearchEngine.java
```

Or compile first, then run the compiled class:

```bash
javac MiniSearchEngine.java
java MiniSearchEngine
```

Run a scripted demonstration instead of opening the prompt:

```bash
java MiniSearchEngine.java --demo
```

The program reads `.txt` files from `documents/`, in filename order. The repository includes 50 sample documents. If the folder is missing or contains no `.txt` files, the program creates 50 sample documents before indexing.

## Search commands

At the prompt, enter a search or command:

| Command | Description |
| --- | --- |
| `rocket orbit` | Search for documents containing any query term; results are ranked by score. |
| `all: garlic olive` | Return documents containing every query term. |
| `/complete pro` | Suggest up to eight indexed terms beginning with `pro`. |
| `/related 0` | Find documents related to document ID `0`, up to two graph hops away. |
| `/open 0` | Print the full text of document ID `0`. |
| `/history` | Show up to ten recent searches, newest first. |
| `/back` | Re-run the previous search. |
| `/help` | Show the command list. |
| `/quit` | Exit the program. |

Document IDs are assigned from `0` in sorted filename order when the index is built. Search snippets show up to 90 characters around a matching term.

## How it works

- Text is lowercased, split into letter-and-number terms, and filtered through a small built-in stopword list.
- A custom hash table stores an inverted index of terms and their document postings. Term frequency is kept in linked posting lists.
- Search scores combine term frequency normalized by document length with inverse document frequency. The default search uses any matching query term; `all:` intersects postings so every query term must be present.
- A trie provides prefix autocomplete. A min-heap keeps the top results, followed by merge sort for display order.
- Unknown terms may receive a "Did you mean?" suggestion based on edit distance.
- A document graph connects files that share at least two of their top five weighted terms; breadth-first search finds related documents.
- A stack stores search history, and a queue is used while loading files and traversing the graph.

The source code also demonstrates arrays, binary search, recursion, and dynamic programming. All indexing is in memory and is rebuilt each time the program starts.

## Repository layout

```text
MiniSearchEngine.java   Search engine implementation and command-line interface
documents/              Sample plain-text documents indexed at startup
LICENSE                 MIT License
```

## License

This project is distributed under the MIT License. See [LICENSE](LICENSE).
