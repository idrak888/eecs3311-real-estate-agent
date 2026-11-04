package ca.yorku.realestate.agent.tool;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.finance.MortgageCalculator;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.persistence.ListingRepository;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds a comparison matrix for two to four listings (F08). The matrix is deterministic; the
 * agent only summarizes it.
 *
 * <p>Parameter: {@value #LISTING_IDS} (list of IDs). Duplicates are ignored. Fewer than two
 * distinct IDs is an error; more than {@value #MAX_LISTINGS} are capped and the result notes it
 * with {@value #CAPPED}. Data returned: {@value #COMPARISON} (a {@link ComparisonResult}).
 */
public class ComparisonTool implements AgentTool {

    public static final String NAME = "compare";
    public static final String LISTING_IDS = "listingIds";
    public static final String COMPARISON = "comparison";
    public static final String CAPPED = "capped";
    public static final int MIN_LISTINGS = 2;
    public static final int MAX_LISTINGS = 4;

    public static final String PRICE = "Price";
    public static final String SQUARE_FEET = "Square feet";
    public static final String BEDROOMS = "Bedrooms";
    public static final String BATHROOMS = "Bathrooms";
    public static final String AGE = "Age (years)";
    public static final String PRICE_PER_SQFT = "Price per sq ft";
    public static final String MONTHLY_PAYMENT = "Monthly payment";
    public static final String TYPE = "Type";
    public static final String NEIGHBORHOOD = "Neighborhood";
    public static final String FEATURES = "Features";

    private final ListingRepository repository;
    private final MortgageCalculator calculator;
    private final int currentYear;

    public ComparisonTool(ListingRepository repository, MortgageCalculator calculator) {
        this(repository, calculator, Year.now().getValue());
    }

    /** @param currentYear year used to compute property age (fixed in tests) */
    public ComparisonTool(ListingRepository repository, MortgageCalculator calculator, int currentYear) {
        this.repository = repository;
        this.calculator = calculator;
        this.currentYear = currentYear;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "side-by-side comparison of 2 to 4 specific listings; params: listingIds (list)";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Set<String> ids = new LinkedHashSet<>(request.getStringList(LISTING_IDS));
        if (ids.size() < MIN_LISTINGS) {
            return ToolResult.failure("Select at least two different properties to compare.");
        }
        List<Listing> listings = new ArrayList<>();
        for (String id : ids) {
            Listing l = repository.findById(id).orElse(null);
            if (l == null) {
                return ToolResult.failure("No listing with ID " + id);
            }
            listings.add(l);
        }
        boolean capped = listings.size() > MAX_LISTINGS;
        if (capped) {
            listings = new ArrayList<>(listings.subList(0, MAX_LISTINGS));
        }
        ComparisonResult result = buildMatrix(listings);
        return ToolResult.success(result.getListings(), Map.of(COMPARISON, result, CAPPED, capped));
    }

    /** Builds the matrix and marks the leader of each numeric row. */
    ComparisonResult buildMatrix(List<Listing> listings) {
        Map<String, List<Object>> rows = new LinkedHashMap<>();
        Map<String, String> leaders = new LinkedHashMap<>();
        Set<String> numeric = new HashSet<>();

        numericRow(rows, leaders, numeric, listings, PRICE, false, Listing::getPrice);
        numericRow(rows, leaders, numeric, listings, SQUARE_FEET, true,
                l -> l.hasSquareFeet() ? (double) l.getSquareFeet() : null);
        numericRow(rows, leaders, numeric, listings, BEDROOMS, true, l -> (double) l.getBedrooms());
        numericRow(rows, leaders, numeric, listings, BATHROOMS, true, l -> (double) l.getBathrooms());
        numericRow(rows, leaders, numeric, listings, AGE, false, l -> (double) l.getAge(currentYear));
        numericRow(rows, leaders, numeric, listings, PRICE_PER_SQFT, false,
                l -> l.hasSquareFeet() ? l.getPricePerSqFt() : null);
        numericRow(rows, leaders, numeric, listings, MONTHLY_PAYMENT, false,
                l -> calculator.monthlyPayment(l.getPrice(), MortgageTerms.defaultsFor(l.getPrice())));

        rows.put(TYPE, listings.stream().map(l -> (Object) l.getType().name().toLowerCase(java.util.Locale.ROOT)).toList());
        rows.put(NEIGHBORHOOD, listings.stream().map(l -> (Object) l.getNeighborhood()).toList());
        rows.put(FEATURES, listings.stream().map(l -> (Object) l.getFeatures()).toList());
        return new ComparisonResult(listings, rows, leaders, numeric);
    }

    private static void numericRow(Map<String, List<Object>> rows, Map<String, String> leaders,
                                   Set<String> numeric, List<Listing> listings, String name,
                                   boolean higherIsBetter, Function<Listing, Double> value) {
        List<Object> values = new ArrayList<>();
        Double best = null;
        String leader = null;
        boolean tie = false;
        for (Listing l : listings) {
            Double v = value.apply(l);
            values.add(v);
            if (v == null || v.isNaN()) {
                continue;
            }
            if (best == null || (higherIsBetter ? v > best : v < best)) {
                best = v;
                leader = l.getId();
                tie = false;
            } else if (v.equals(best)) {
                tie = true;
            }
        }
        rows.put(name, values);
        numeric.add(name);
        long available = values.stream().filter(java.util.Objects::nonNull).count();
        if (leader != null && !tie && available >= 2) {
            leaders.put(name, leader);
        }
    }
}
