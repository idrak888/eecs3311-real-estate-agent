package ca.yorku.realestate.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.persistence.parser.ListingParserFactory;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** F03 end to end: bundled dataset -> criteria -> Composite filter -> repository. */
class CriteriaSearchIntegrationTest {

    private static ListingRepository loadedRepository() throws IOException, URISyntaxException {
        ListingRepository repo = new ListingRepository();
        File data = new File(CriteriaSearchIntegrationTest.class.getResource("/data/listings.json").toURI());
        new ListingImporter(new ListingParserFactory(), repo).importFile(data);
        return repo;
    }

    private static List<String> ids(List<Listing> listings) {
        return listings.stream().map(Listing::getId).toList();
    }

    @Test
    void priceBedsAndCondoOrTownhouse() throws Exception {
        SearchCriteria c = new SearchCriteriaBuilder()
                .withMaxPrice(1_000_000.0).withMinBedrooms(2)
                .withType(PropertyType.CONDO).withType(PropertyType.TOWNHOUSE).build();
        assertEquals(List.of("L002", "L005", "L010", "L014", "L033", "L038", "L040", "L042"),
                ids(loadedRepository().findMatching(c.toFilter())));
    }

    @Test
    void neighborhoodsAndBathrooms() throws Exception {
        SearchCriteria c = new SearchCriteriaBuilder()
                .withNeighborhood("scarborough").withNeighborhood("Mississauga")
                .withMinBathrooms(2).build();
        List<Listing> result = loadedRepository().findMatching(c.toFilter());
        assertEquals(7, result.size());
        assertTrue(result.stream().allMatch(l -> l.getBathrooms() >= 2));
    }

    @Test
    void houseWithGarage() throws Exception {
        SearchCriteria c = new SearchCriteriaBuilder().withType(PropertyType.HOUSE).withFeature("garage").build();
        List<Listing> result = loadedRepository().findMatching(c.toFilter());
        assertEquals(8, result.size());
        assertTrue(result.stream().allMatch(l -> l.getType() == PropertyType.HOUSE && l.hasFeature("garage")));
    }

    @Test
    void clearedCriteriaReturnFullSetNotEmpty() throws Exception {
        assertEquals(60, loadedRepository().findMatching(SearchCriteria.empty().toFilter()).size());
    }
}
