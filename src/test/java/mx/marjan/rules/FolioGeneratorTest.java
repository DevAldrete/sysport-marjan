package mx.marjan.rules;

import mx.marjan.requests.FolioGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FolioGeneratorTest {

    @Test
    void formatsFolioWithYearAndSequence() { // BR-01
        assertEquals("SR-2026-000123", FolioGenerator.format(2026, 123));
        assertEquals("SR-2026-000001", FolioGenerator.format(2026, 1));
    }

    @Test
    void readsYearBack() {
        assertEquals(2026, FolioGenerator.yearOf("SR-2026-000123"));
    }
}
