package mx.marjan.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextTest {

    @Test
    void truncatesLongTextWithEllipsis() {
        assertEquals("abcdefg\u2026", Text.truncate("abcdefghij", 8));
    }

    @Test
    void collapsesWhitespaceAndNewlines() {
        assertEquals("a b c", Text.truncate("  a\n b\t c  ", 20));
    }

    @Test
    void keepsShortText() {
        assertEquals("hola", Text.truncate("hola", 10));
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", Text.truncate(null, 10));
    }
}
