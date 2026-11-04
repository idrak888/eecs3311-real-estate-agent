package ca.yorku.realestate.testsupport;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.persistence.ListingImporter;
import ca.yorku.realestate.persistence.ListingRepository;
import ca.yorku.realestate.persistence.parser.ListingParserFactory;
import java.io.File;
import java.util.List;

/** Shared fixtures for tests outside the persistence package. */
public final class TestData {

    private TestData() {
    }

    /** Repository loaded with the bundled 60-listing dataset. */
    public static ListingRepository bundledRepository() {
        try {
            ListingRepository repo = new ListingRepository();
            File data = new File(TestData.class.getResource("/data/listings.json").toURI());
            new ListingImporter(new ListingParserFactory(), repo).importFile(data);
            return repo;
        } catch (Exception e) {
            throw new IllegalStateException("Could not load bundled listings", e);
        }
    }

    /** Repository holding exactly the given listings. */
    public static ListingRepository repositoryOf(Listing... listings) {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listings));
        return repo;
    }

    public static Listing listing(String id, double price, int beds, int baths, int sqft,
                                  PropertyType type, String hood, int year, String... features) {
        return new Listing(id, price, beds, baths, sqft, type, hood, year, List.of(features));
    }
}
