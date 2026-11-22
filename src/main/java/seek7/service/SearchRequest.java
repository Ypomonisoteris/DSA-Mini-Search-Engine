package seek7.service;

import java.util.Objects;

/** Normalized query text and the selected mode for one search operation. */
public record SearchRequest(String query, SearchEngineService.Mode mode) {
    public SearchRequest {
        query = Objects.requireNonNull(query, "query");
        mode = Objects.requireNonNull(mode, "mode");
    }

    public static SearchRequest parse(String rawQuery, SearchEngineService.Mode selectedMode) {
        String query = rawQuery == null ? "" : rawQuery.trim().replaceAll("\\s+", " ");
        SearchEngineService.Mode mode = Objects.requireNonNull(selectedMode, "selectedMode");
        if (query.regionMatches(true, 0, "all:", 0, 4)) {
            query = query.substring(4).trim().replaceAll("\\s+", " ");
            mode = SearchEngineService.Mode.ALL_TERMS;
        }
        return new SearchRequest(query, mode);
    }

    public boolean isValid() {
        return !query.isEmpty();
    }
}
