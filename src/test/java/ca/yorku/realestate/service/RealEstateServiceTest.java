package ca.yorku.realestate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.domain.finance.Verdict;
import ca.yorku.realestate.domain.ranking.PriceAscendingStrategy;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import ca.yorku.realestate.persistence.LoadSummary;
import ca.yorku.realestate.persistence.PreferenceStore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealEstateServiceTest {

    /** Records every notification for assertions. */
    static class RecordingObserver implements SearchObserver {
        final List<List<Listing>> results = new ArrayList<>();
        final List<SearchCriteria> criteria = new ArrayList<>();
        final List<List<String>> shortlists = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        @Override
        public void onResultsChanged(List<Listing> r) {
            results.add(r);
        }

        @Override
        public void onCriteriaChanged(SearchCriteria c) {
            criteria.add(c);
        }

        @Override
        public void onShortlistChanged(Shortlist s) {
            shortlists.add(s.getEntries());
        }

        @Override
        public void onWarning(String message) {
            warnings.add(message);
        }
    }

    private static Path tempDir() throws IOException {
        Path dir = Files.createTempDirectory("svc");
        dir.toFile().deleteOnExit();
        return dir;
    }

    private static File dataset() {
        try {
            return new File(RealEstateServiceTest.class.getResource("/data/listings.json").toURI());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RealEstateService loaded(Path prefs) throws IOException {
        RealEstateService s = RealEstateService.create(new PreferenceStore(prefs));
        s.loadListings(dataset());
        return s;
    }

    private static List<String> ids(List<Listing> l) {
        return l.stream().map(Listing::getId).toList();
    }

    // ---- loading + observers ----

    @Test
    void loadShowsAllListingsAndNotifies() throws IOException {
        RealEstateService s = RealEstateService.create(new PreferenceStore(tempDir()));
        RecordingObserver o = new RecordingObserver();
        s.addObserver(o);
        LoadSummary summary = s.loadListings(dataset());
        assertEquals(60, summary.loaded());
        assertEquals(60, s.getResults().size());
        assertEquals(1, o.results.size());
        assertEquals(60, o.results.get(0).size());
    }

    @Test
    void observerAddedTwiceIsNotifiedOnceAndCanBeRemoved() throws IOException {
        RealEstateService s = loaded(tempDir());
        RecordingObserver o = new RecordingObserver();
        s.addObserver(o);
        s.addObserver(o);
        s.search(SearchCriteria.empty());
        assertEquals(1, o.results.size());
        s.removeObserver(o);
        s.search(SearchCriteria.empty());
        assertEquals(1, o.results.size());
    }

    // ---- search / rank ----

    @Test
    void searchFiltersRanksAndNotifiesBothFrontends() throws IOException {
        RealEstateService s = loaded(tempDir());
        RecordingObserver gui = new RecordingObserver();
        RecordingObserver cli = new RecordingObserver();
        s.addObserver(gui);
        s.addObserver(cli);
        s.setRankingStrategy(new PriceAscendingStrategy());

        SearchCriteria c = new SearchCriteriaBuilder().withMaxPrice(1_000_000.0).withMinBedrooms(2)
                .withType(PropertyType.CONDO).withType(PropertyType.TOWNHOUSE).build();
        List<Listing> r = s.search(c);

        assertEquals(8, r.size());
        for (int i = 1; i < r.size(); i++) {
            assertTrue(r.get(i - 1).getPrice() <= r.get(i).getPrice());
        }
        assertEquals(c, s.getCriteria());
        assertEquals(r, gui.results.get(gui.results.size() - 1));
        assertEquals(r, cli.results.get(cli.results.size() - 1));
        assertEquals(c, gui.criteria.get(gui.criteria.size() - 1));
    }

    @Test
    void invalidCriteriaDoNotSearchOrChangeState() throws IOException {
        RealEstateService s = loaded(tempDir());
        SearchCriteria before = s.getCriteria();
        RecordingObserver o = new RecordingObserver();
        s.addObserver(o);
        InvalidCriteriaException e = assertThrows(InvalidCriteriaException.class, () -> s.search(
                new SearchCriteriaBuilder().withMinPrice(900_000.0).withMaxPrice(500_000.0).build()));
        assertTrue(e.getErrors().get(0).contains("exceeds maximum"));
        assertEquals(before, s.getCriteria());
        assertTrue(o.results.isEmpty());
    }

    @Test
    void refineMergesIntoSessionCriteria() throws IOException {
        RealEstateService s = loaded(tempDir());
        s.search(new SearchCriteriaBuilder().withMinBedrooms(3).build());
        s.refine(new SearchCriteriaBuilder().withType(PropertyType.HOUSE).build());
        assertEquals(Integer.valueOf(3), s.getCriteria().getMinBedrooms());
        assertTrue(s.getResults().stream().allMatch(l -> l.getType() == PropertyType.HOUSE && l.getBedrooms() >= 3));
    }

    @Test
    void changingStrategyReranksWithoutResearching() throws IOException {
        RealEstateService s = loaded(tempDir());
        s.search(new SearchCriteriaBuilder().withType(PropertyType.CONDO).build());
        List<String> before = ids(s.getResults());
        s.setRankingStrategy(new PriceAscendingStrategy());
        List<String> after = ids(s.getResults());
        assertEquals(new java.util.HashSet<>(before), new java.util.HashSet<>(after), "same listings");
        for (int i = 1; i < s.getResults().size(); i++) {
            assertTrue(s.getResults().get(i - 1).getPrice() <= s.getResults().get(i).getPrice());
        }
        assertEquals("Price (low to high)", s.getRankingStrategy().getName());
        assertThrows(IllegalArgumentException.class, () -> s.setRankingStrategy(null));
    }

    // ---- detail / mortgage / affordability ----

    @Test
    void unknownListingIsEmptyOrNamedError() throws IOException {
        RealEstateService s = loaded(tempDir());
        assertTrue(s.getListing("NOPE").isEmpty());
        NoSuchListingException e = assertThrows(NoSuchListingException.class,
                () -> s.calculateMortgage("NOPE", null));
        assertEquals("No listing with ID NOPE", e.getMessage());
    }

    @Test
    void mortgageUsesDefaultsWhenTermsOmitted() throws IOException {
        RealEstateService s = loaded(tempDir());
        Listing l = s.getListing("L001").orElseThrow();
        MortgageResult r = s.calculateMortgage("L001", null);
        assertEquals(l.getPrice() * 0.8, r.getPrincipal(), 1e-6);
        MortgageResult custom = s.calculateMortgage("L001", new MortgageTerms(l.getPrice(), 5, 25));
        assertFalse(custom.isFinancingRequired());
    }

    @Test
    void affordabilityRequiresAProfile() throws IOException {
        RealEstateService s = loaded(tempDir());
        assertThrows(IllegalStateException.class, () -> s.assessAffordability("L001", null));
        assertThrows(IllegalStateException.class, s::maxSupportablePrice);
        s.setFinancialProfile(new FinancialProfile(400_000, 0, 400_000));
        assertEquals(Verdict.AFFORDABLE, s.assessAffordability("L002", null).getVerdict());
        assertEquals(Verdict.UNAFFORDABLE,
                s.assessAffordability("L002", new FinancialProfile(0, 0, 0)).getVerdict());
    }

    // ---- shortlist ----

    @Test
    void shortlistAddUndoPersistsAndNotifies() throws IOException {
        Path prefs = tempDir();
        RealEstateService s = loaded(prefs);
        RecordingObserver o = new RecordingObserver();
        s.addObserver(o);

        assertTrue(s.addToShortlist("L003"));
        assertTrue(s.addToShortlist("L001"));
        assertFalse(s.addToShortlist("L003"), "duplicate is a no-op");
        assertEquals(List.of("L003", "L001"), new PreferenceStore(prefs).loadShortlist().getEntries());
        assertEquals(List.of("L003", "L001"), ids(s.getShortlistedListings()));

        assertEquals("Add L001 to shortlist", s.undo().orElseThrow());
        assertEquals(List.of("L003"), new PreferenceStore(prefs).loadShortlist().getEntries());
        assertEquals(List.of("L003"), o.shortlists.get(o.shortlists.size() - 1));
    }

    @Test
    void addingUnknownListingIsRejected() throws IOException {
        RealEstateService s = loaded(tempDir());
        assertThrows(NoSuchListingException.class, () -> s.addToShortlist("NOPE"));
        assertTrue(s.getShortlist().isEmpty());
    }

    @Test
    void undoWithNothingToUndoReportsEmpty() throws IOException {
        RealEstateService s = loaded(tempDir());
        assertFalse(s.canUndo());
        assertTrue(s.undo().isEmpty());
    }

    @Test
    void removeAndUndoRestores() throws IOException {
        RealEstateService s = loaded(tempDir());
        s.addToShortlist("L001");
        s.addToShortlist("L002");
        assertTrue(s.removeFromShortlist("L001"));
        assertFalse(s.removeFromShortlist("L001"));
        s.undo();
        assertEquals(List.of("L001", "L002"), s.getShortlist().getEntries());
    }

    @Test
    void saveFailureWarnsButKeepsInMemoryShortlist() throws IOException {
        Path notADir = Files.createTempFile("blocked", ".txt");
        notADir.toFile().deleteOnExit();
        RealEstateService s = loaded(notADir);
        RecordingObserver o = new RecordingObserver();
        s.addObserver(o);
        s.addToShortlist("L001");
        assertTrue(s.getShortlist().contains("L001"));
        assertEquals(1, o.warnings.size());
        assertTrue(o.warnings.get(0).contains("will not persist"));
    }

    @Test
    void restoreSessionLoadsSavedShortlistAndProfile() throws IOException {
        Path prefs = tempDir();
        RealEstateService first = loaded(prefs);
        first.addToShortlist("L010");
        first.setFinancialProfile(new FinancialProfile(120_000, 300, 90_000));

        RealEstateService second = loaded(prefs);
        assertTrue(second.restoreSession().isEmpty());
        assertEquals(List.of("L010"), second.getShortlist().getEntries());
        assertEquals(120_000, second.getFinancialProfile().orElseThrow().getAnnualIncome(), 1e-9);
        assertFalse(second.canUndo(), "undo history does not carry across sessions");
    }

    @Test
    void restoreSessionWarnsOnCorruptFiles() throws IOException {
        Path prefs = tempDir();
        Files.writeString(prefs.resolve(PreferenceStore.SHORTLIST_FILE), "garbage");
        RealEstateService s = loaded(prefs);
        RecordingObserver o = new RecordingObserver();
        s.addObserver(o);
        List<String> warnings = s.restoreSession();
        assertEquals(1, warnings.size());
        assertEquals(warnings, o.warnings);
        assertTrue(s.getShortlist().isEmpty());
    }

    // ---- zero-match diagnosis ----

    @Test
    void emptySearchIsDiagnosedButNonEmptyIsNot() throws IOException {
        RealEstateService s = loaded(tempDir());
        s.search(new SearchCriteriaBuilder().withMaxPrice(1_500_000.0).withMinBedrooms(3).withType(PropertyType.HOUSE)
                .withNeighborhood("Vaughan").withNeighborhood("Woodbridge").withFeature("garage").build());
        assertTrue(s.getResults().isEmpty());
        assertTrue(s.diagnoseNoResults().orElseThrow().message().contains("the area Vaughan or Woodbridge"));

        s.search(new SearchCriteriaBuilder().withMinBedrooms(3).build());
        assertTrue(s.diagnoseNoResults().isEmpty());
        s.search(SearchCriteria.empty());
        assertTrue(s.diagnoseNoResults().isEmpty());
    }
}
