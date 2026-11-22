import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import seek7.service.SearchEngineService;

/**
 * Adapts the legacy default-package engine to the named-package desktop app.
 * The algorithms remain in MiniSearchEngine; this class only maps its API.
 */
public final class MiniSearchEngineServiceProvider implements SearchEngineService {
    private static final int MAX_AUTOCOMPLETE_SUGGESTIONS = 8;
    private static final int SNIPPET_WIDTH = 90;

    private MiniSearchEngine.Engine engine = new MiniSearchEngine.Engine();

    @Override
    public void load(Path folder) throws IOException {
        engine = new MiniSearchEngine.Engine();
        if (Files.isDirectory(folder)) {
            engine.loadFolder(folder);
        }
    }

    @Override
    public int documentCount() {
        return engine.docs.size();
    }

    @Override
    public int uniqueTermCount() {
        return engine.index.count;
    }

    @Override
    public AutocompleteResult autocomplete(String query, int limit) {
        query = query == null ? "" : query;
        int replacementStart = currentTokenStart(query);
        int safeLimit = Math.min(MAX_AUTOCOMPLETE_SUGGESTIONS, Math.max(0, limit));
        if (safeLimit == 0 || replacementStart == query.length()) {
            return new AutocompleteResult(replacementStart, List.of());
        }

        String prefix = query.substring(replacementStart).toLowerCase(Locale.ROOT);
        List<String> matches = engine.trie.autocomplete(prefix, safeLimit);
        return new AutocompleteResult(replacementStart, matches);
    }

    @Override
    public SearchResponse search(String query, Mode mode, int previewLimit) {
        int allMatchesLimit = Math.max(1, engine.docs.size());
        MiniSearchEngine.SearchResult result = engine.search(
                query, allMatchesLimit, mode == Mode.ALL_TERMS);

        List<SearchHit> hits = new ArrayList<>();
        int hitLimit = Math.min(Math.max(0, previewLimit), result.ranked.size());
        List<String> terms = MiniSearchEngine.tokenize(query);
        for (int i = 0; i < hitLimit; i++) {
            MiniSearchEngine.Scored score = result.ranked.get(i);
            MiniSearchEngine.Document document = engine.docs.get(score.docId);
            hits.add(new SearchHit(document.id, document.name, score.score,
                    engine.snippet(document, terms, SNIPPET_WIDTH)));
        }

        List<SpellingHint> spellingHints = new ArrayList<>();
        for (String[] hint : result.hints) {
            spellingHints.add(new SpellingHint(hint[0], hint[1]));
        }
        return new SearchResponse(result.ranked.size(), hits, spellingHints);
    }

    private static int currentTokenStart(String query) {
        if (query == null || query.isEmpty()) {
            return query == null ? 0 : query.length();
        }

        int end = query.length();
        if (!isAsciiLetterOrDigit(Character.toLowerCase(query.charAt(end - 1)))) {
            return end;
        }

        int start = end - 1;
        while (start > 0 && isAsciiLetterOrDigit(Character.toLowerCase(query.charAt(start - 1)))) {
            start--;
        }
        return start;
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
    }
}
