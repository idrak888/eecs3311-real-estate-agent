package ca.yorku.realestate.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingRepositoryTest {

    private static Listing listing(String id, double price) {
        return new Listing(id, price, 2, 1, 900, PropertyType.CONDO, "Downtown", 2015, null);
    }

    @Test
    void emptyRepositoryHasNothing() {
        ListingRepository repo = new ListingRepository();
        assertEquals(0, repo.count());
        assertTrue(repo.findAll().isEmpty());
        assertTrue(repo.findById("L1").isEmpty());
    }

    @Test
    void findAllPreservesLoadOrder() {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listing("B", 2), listing("A", 1), listing("C", 3)));
        assertEquals(List.of("B", "A", "C"), repo.findAll().stream().map(Listing::getId).toList());
    }

    @Test
    void findByIdTrimsAndHandlesUnknownAndNull() {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listing("L1", 1)));
        assertEquals("L1", repo.findById("  L1 ").orElseThrow().getId());
        assertTrue(repo.findById("L9").isEmpty());
        assertTrue(repo.findById(null).isEmpty());
    }

    @Test
    void duplicateIdsKeepFirstAndAreReported() {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listing("L1", 100), listing("L2", 200), listing("L1", 999)));
        assertEquals(2, repo.count());
        assertEquals(100, repo.findById("L1").orElseThrow().getPrice(), 1e-9);
        assertEquals(List.of("L1"), repo.getDuplicateIds());
    }

    @Test
    void loadReplacesPreviousContents() {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listing("L1", 1), listing("L1", 1)));
        repo.load(List.of(listing("L2", 2)));
        assertEquals(1, repo.count());
        assertTrue(repo.findById("L1").isEmpty());
        assertTrue(repo.getDuplicateIds().isEmpty());
    }

    @Test
    void loadingNullClearsRepository() {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listing("L1", 1)));
        repo.load(null);
        assertEquals(0, repo.count());
    }

    @Test
    void findAllIsNotAViewOfInternalState() {
        ListingRepository repo = new ListingRepository();
        repo.load(List.of(listing("L1", 1)));
        List<Listing> snapshot = repo.findAll();
        repo.load(List.of());
        assertEquals(1, snapshot.size());
    }
}
