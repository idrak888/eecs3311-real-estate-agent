package ca.yorku.realestate.persistence.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingParserFactoryTest {

    private final ListingParserFactory factory = new ListingParserFactory();

    @Test
    void choosesParserByExtension() {
        assertInstanceOf(JsonListingParser.class, factory.createParser(new File("data/listings.json")));
        assertInstanceOf(CsvListingParser.class, factory.createParser(new File("listings.CSV")));
    }

    @Test
    void returnsFreshParserEachCall() {
        File f = new File("a.json");
        assertNotSame(factory.createParser(f), factory.createParser(f));
    }

    @Test
    void unsupportedExtensionNamesSupportedFormats() {
        UnsupportedFormatException e = assertThrows(UnsupportedFormatException.class,
                () -> factory.createParser(new File("listings.xml")));
        assertEquals(List.of(".json", ".csv"), e.getSupportedFormats());
        assertTrue(e.getMessage().contains("listings.xml"));
        assertTrue(e.getMessage().contains(".json, .csv"));
    }

    @Test
    void fileWithoutExtensionIsUnsupported() {
        assertThrows(UnsupportedFormatException.class, () -> factory.createParser(new File("listings")));
        assertThrows(UnsupportedFormatException.class, () -> factory.createParser(new File("listings.")));
    }

    @Test
    void newFormatsCanBeRegisteredWithoutChangingCallers() {
        ListingParser tsv = new ListingParser() {
            @Override
            public List<ca.yorku.realestate.domain.Listing> parse(File file) {
                return List.of();
            }

            @Override
            public boolean supports(String extension) {
                return "tsv".equals(extension);
            }

            @Override
            public List<String> getRejections() {
                return List.of();
            }
        };
        factory.register("tsv", () -> tsv);
        assertEquals(tsv, factory.createParser(new File("a.tsv")));
        assertEquals(List.of(".json", ".csv", ".tsv"), factory.supportedFormats());
    }
}
