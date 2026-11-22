package seek7.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Stable boundary between SEEK7's JavaFX layer and the existing search engine. */
public interface SearchEngineService {
    enum Mode {
        ANY_TERM,
        ALL_TERMS
    }

    record AutocompleteResult(int replacementStart, List<String> suggestions) {
        public AutocompleteResult {
            suggestions = List.copyOf(suggestions);
        }
    }

    record SearchHit(int documentId, String fileName, double score, String snippet) {
    }

    record SpellingHint(String typedTerm, String suggestion) {
    }

    record SearchResponse(int totalMatches, List<SearchHit> hits, List<SpellingHint> spellingHints) {
        public SearchResponse {
            hits = List.copyOf(hits);
            spellingHints = List.copyOf(spellingHints);
        }
    }

    record IndexedDocument(int documentId, String fileName, String content) {
    }

    void load(Path folder) throws IOException;

    int documentCount();

    int uniqueTermCount();

    AutocompleteResult autocomplete(String query, int limit);

    SearchResponse search(String query, Mode mode, int resultLimit);

    Optional<IndexedDocument> documentById(int documentId);
}
