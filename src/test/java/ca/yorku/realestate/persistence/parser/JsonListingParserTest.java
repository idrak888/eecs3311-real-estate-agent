package ca.yorku.realestate.persistence.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonListingParserTest {

    private final JsonListingParser parser = new JsonListingParser();

    @Test
    void parsesTopLevelArray() throws IOException {
        List<Listing> listings = parser.parse(Fixtures.file("valid.json"));
        assertEquals(3, listings.size());
        assertTrue(parser.getRejections().isEmpty());

        Listing first = listings.get(0);
        assertEquals("T001", first.getId());
        assertEquals(850_000, first.getPrice(), 1e-9);
        assertEquals(PropertyType.HOUSE, first.getType());
        assertEquals(List.of("garage", "backyard"), first.getFeatures());
    }

    @Test
    void acceptsFeaturesAsSemicolonString() throws IOException {
        Listing condo = parser.parse(Fixtures.file("valid.json")).get(1);
        assertEquals(List.of("gym", "concierge"), condo.getFeatures());
    }

    @Test
    void missingSquareFeetIsUnknownNotRejected() throws IOException {
        Listing town = parser.parse(Fixtures.file("valid.json")).get(2);
        assertFalse(town.hasSquareFeet());
    }

    @Test
    void skipsMalformedRecordsAndContinues() throws IOException {
        List<Listing> listings = parser.parse(Fixtures.file("mixed.json"));
        assertEquals(List.of("M001", "M006"), listings.stream().map(Listing::getId).toList());

        List<String> rejections = parser.getRejections();
        assertEquals(4, rejections.size());
        assertTrue(rejections.get(0).contains("id=M002") && rejections.get(0).contains("price"));
        assertTrue(rejections.get(1).contains("id=M003") && rejections.get(1).contains("castle"));
        assertTrue(rejections.get(2).contains("record 4") && rejections.get(2).contains("object"));
        assertTrue(rejections.get(3).contains("record 5") && rejections.get(3).contains("id is required"));
    }

    @Test
    void rejectionsResetBetweenParses() throws IOException {
        parser.parse(Fixtures.file("mixed.json"));
        parser.parse(Fixtures.file("valid.json"));
        assertTrue(parser.getRejections().isEmpty());
    }

    @Test
    void wrongOverallShapeFailsTheParse() {
        assertThrows(IOException.class, () -> parser.parse(Fixtures.file("badshape.json")));
    }

    @Test
    void syntacticallyInvalidJsonFailsTheParse() {
        assertThrows(IOException.class, () -> parser.parse(Fixtures.file("malformed.json")));
    }

    @Test
    void missingFileFailsTheParse() {
        assertThrows(IOException.class, () -> parser.parse(new File("does-not-exist.json")));
    }

    @Test
    void supportsOnlyJson() {
        assertTrue(parser.supports("json"));
        assertTrue(parser.supports("JSON"));
        assertFalse(parser.supports("csv"));
    }
}
