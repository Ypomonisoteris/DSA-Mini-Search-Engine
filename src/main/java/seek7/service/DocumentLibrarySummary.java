package seek7.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/** Reads a small, factual summary of the local text-file library. */
public final class DocumentLibrarySummary {
    private DocumentLibrarySummary() {
    }

    public static long countTextDocuments(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return 0;
        }

        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(DocumentLibrarySummary::isTextDocument)
                    .count();
        }
    }

    private static boolean isTextDocument(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt");
    }
}
