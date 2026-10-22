package ca.yorku.realestate.persistence.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvListingParserTest {

    private final CsvListingParser parser = new CsvListingParser();

    @Test
    void parsesValidFileSkippingBlankLines() throws IOException {
        List<Listing> listings = parser.parse(Fixtures.file("valid.csv"));
        assertEquals(3, listings.size());
        assertTrue(parser.getRejections().isEmpty());
        assertEquals(List.of("garage", "backyard"), listings.get(0).getFeatures());
    }

    @Test
    void quotedFieldsMayContainCommas() throws IOException {
        Listing condo = parser.parse(Fixtures.file("valid.csv")).get(1);
        assertEquals("Downtown, Core", condo.getNeighborhood());
        assertEquals(List.of("gym", "concierge"), condo.getFeatures());
    }

    @Test
    void emptySquareFeetIsUnknown() throws IOException {
        Listing town = parser.parse(Fixtures.file("valid.csv")).get(2);
        assertFalse(town.hasSquareFeet());
        assertTrue(town.getFeatures().isEmpty());
    }

    @Test
    void headerMatchingIgnoresOrderCaseAndSeparators() throws IOException {
        List<Listing> listings = parser.parse(Fixtures.file("reordered.csv"));
        assertEquals(1, listings.size());
        Listing l = listings.get(0);
        assertEquals("R001", l.getId());
        assertEquals(PropertyType.CONDO, l.getType());
        assertEquals(600, l.getSquareFeet());
        assertEquals(2020, l.getYearBuilt());
    }

    @Test
    void skipsMalformedRowsWithReasons() throws IOException {
        List<Listing> listings = parser.parse(Fixtures.file("mixed.csv"));
        assertEquals(List.of("X001", "X006"), listings.stream().map(Listing::getId).toList());
        assertEquals(3, listings.get(1).getBedrooms(), "3.0 is accepted as a whole number");

        List<String> rejections = parser.getRejections();
        assertEquals(4, rejections.size());
        assertTrue(rejections.get(0).contains("id=X002") && rejections.get(0).contains("price"));
        assertTrue(rejections.get(1).contains("columns"));
        assertTrue(rejections.get(2).contains("id=X004") && rejections.get(2).contains("whole number"));
        assertTrue(rejections.get(3).contains("unterminated"));
    }

    @Test
    void emptyFileFailsTheParse() throws IOException {
        File empty = Files.createTempFile("empty", ".csv").toFile();
        empty.deleteOnExit();
        assertThrows(IOException.class, () -> parser.parse(empty));
    }

    @Test
    void splitLineHandlesEscapedQuotes() {
        assertEquals(List.of("a", "say \"hi\"", ""), CsvListingParser.splitLine("a,\"say \"\"hi\"\"\","));
    }

    @Test
    void supportsOnlyCsv() {
        assertTrue(parser.supports("csv"));
        assertFalse(parser.supports("json"));
    }
}
