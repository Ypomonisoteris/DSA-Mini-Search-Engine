package seek7.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.ServiceLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchEngineServiceIntegrationTest {
    @TempDir
    Path documents;

    private SearchEngineService service;

    @BeforeEach
    void loadFixtureDocuments() throws IOException {
        Files.writeString(documents.resolve("01-both.txt"),
                "rocket orbit rocketry rocketship rockman rockstar rockfall rockhouse rockbase rockpath rockpool rockgarden");
        Files.writeString(documents.resolve("02-rocket.txt"), "rocket engine launch");
        Files.writeString(documents.resolve("03-orbit.txt"), "orbit satellite station");
        Files.writeString(documents.resolve("04-other.txt"), "cooking garlic olive");

        service = ServiceLoader.load(SearchEngineService.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Search engine provider was not registered."));
        service.load(documents);
    }

    @Test
    void anyAndAllModesUseTheExistingEngineSemantics() {
        SearchEngineService.SearchResponse any = service.search(
                "rocket orbit", SearchEngineService.Mode.ANY_TERM, 5);
        SearchEngineService.SearchResponse all = service.search(
                "rocket orbit", SearchEngineService.Mode.ALL_TERMS, 5);

        assertEquals(3, any.totalMatches());
        assertEquals(1, all.totalMatches());
        assertEquals("01-both.txt", all.hits().getFirst().fileName());
    }

    @Test
    void autocompleteUsesIndexedTrieTermsAndNeverExceedsEight() {
        SearchEngineService.AutocompleteResult result = service.autocomplete("rock", 20);
        Set<String> indexedFixtureTerms = Set.of(
                "rocket", "rocketry", "rocketship", "rockman", "rockstar", "rockfall",
                "rockhouse", "rockbase", "rockpath", "rockpool", "rockgarden");

        assertEquals(8, result.suggestions().size());
        assertTrue(result.suggestions().stream().allMatch(indexedFixtureTerms::contains));
        assertTrue(result.suggestions().stream().allMatch(term -> term.startsWith("rock")));
        assertTrue(service.autocomplete("missing", 8).suggestions().isEmpty());
        assertTrue(service.autocomplete("rock ", 8).suggestions().isEmpty());
    }

    @Test
    void typoHintsComeFromTheExistingSearchEngine() {
        SearchEngineService.SearchResponse response = service.search(
                "roket", SearchEngineService.Mode.ANY_TERM, 5);

        assertTrue(response.spellingHints().stream()
                .anyMatch(hint -> hint.typedTerm().equals("roket") && hint.suggestion().equals("rocket")));
    }

    @Test
    void queryNormalizationRejectsBlankTextAndSupportsAllPrefix() {
        SearchRequest blank = SearchRequest.parse(" \t  ", SearchEngineService.Mode.ANY_TERM);
        SearchRequest normalized = SearchRequest.parse("  rocket\t  orbit  ", SearchEngineService.Mode.ANY_TERM);
        SearchRequest all = SearchRequest.parse(" ALL: garlic\n olive ", SearchEngineService.Mode.ANY_TERM);

        assertFalse(blank.isValid());
        assertEquals("rocket orbit", normalized.query());
        assertEquals(SearchEngineService.Mode.ANY_TERM, normalized.mode());
        assertTrue(all.isValid());
        assertEquals("garlic olive", all.query());
        assertEquals(SearchEngineService.Mode.ALL_TERMS, all.mode());
    }
}
