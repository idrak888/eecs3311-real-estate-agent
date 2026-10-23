package ca.yorku.realestate.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.persistence.parser.ListingParserFactory;
import ca.yorku.realestate.persistence.parser.UnsupportedFormatException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ListingImporterTest {

    private final ListingRepository repository = new ListingRepository();
    private final ListingImporter importer = new ListingImporter(new ListingParserFactory(), repository);

    private static File resource(String path) {
        try {
            return new File(ListingImporterTest.class.getResource(path).toURI());
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalStateException("Missing resource " + path, e);
        }
    }

    @Test
    void importsBundledJsonDataset() throws IOException {
        LoadSummary summary = importer.importFile(resource("/data/listings.json"));
        assertEquals(60, summary.loaded());
        assertEquals(0, summary.rejected());
        assertEquals(60, repository.count());
        assertEquals("Loaded 60 listings from listings.json", summary.describe());
    }

    @Test
    void jsonAndCsvDatasetsProduceIdenticalListings() throws IOException {
        importer.importFile(resource("/data/listings.json"));
        var fromJson = repository.findAll();
        importer.importFile(resource("/data/listings.csv"));
        var fromCsv = repository.findAll();

        assertEquals(fromJson.size(), fromCsv.size());
        for (int i = 0; i < fromJson.size(); i++) {
            var a = fromJson.get(i);
            var b = fromCsv.get(i);
            assertEquals(a.getId(), b.getId());
            assertEquals(a.getPrice(), b.getPrice(), 1e-9);
            assertEquals(a.getSquareFeet(), b.getSquareFeet());
            assertEquals(a.getType(), b.getType());
            assertEquals(a.getNeighborhood(), b.getNeighborhood());
            assertEquals(a.getFeatures(), b.getFeatures());
        }
    }

    @Test
    void summaryReportsRejectedRecords() throws IOException {
        LoadSummary summary = importer.importFile(resource("/fixtures/mixed.json"));
        assertEquals(2, summary.loaded());
        assertEquals(4, summary.rejected());
        assertEquals("Loaded 2 listings from mixed.json (4 rejected)", summary.describe());
    }

    @Test
    void duplicateIdsAppearInRejections() throws IOException {
        Path file = Files.createTempFile("dupes", ".csv");
        file.toFile().deleteOnExit();
        Files.writeString(file, String.join("\n",
                "id,price,bedrooms,bathrooms,squareFeet,type,neighborhood,yearBuilt,features",
                "D1,500000,2,1,800,condo,Downtown,2015,",
                "D1,600000,2,1,800,condo,Downtown,2015,"));
        LoadSummary summary = importer.importFile(file.toFile());
        assertEquals(1, summary.loaded());
        assertTrue(summary.rejections().get(0).contains("duplicate id D1"));
    }

    @Test
    void missingFileNamesExpectedPath() {
        FileNotFoundException e = assertThrows(FileNotFoundException.class,
                () -> importer.importFile(new File("nowhere/listings.json")));
        assertTrue(e.getMessage().contains("nowhere"));
    }

    @Test
    void unsupportedFormatIsReported() throws IOException {
        Path file = Files.createTempFile("listings", ".xml");
        file.toFile().deleteOnExit();
        assertThrows(UnsupportedFormatException.class, () -> importer.importFile(file.toFile()));
    }

    @Test
    void failedImportLeavesRepositoryUntouched() throws IOException {
        importer.importFile(resource("/data/listings.json"));
        assertThrows(IOException.class, () -> importer.importFile(resource("/fixtures/malformed.json")));
        assertEquals(60, repository.count());
    }
}
