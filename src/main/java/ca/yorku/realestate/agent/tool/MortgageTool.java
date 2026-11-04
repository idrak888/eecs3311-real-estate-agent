package ca.yorku.realestate.agent.tool;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageCalculator;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.persistence.ListingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes the mortgage for one listing and, when a financial profile is available, its
 * affordability verdict (F06, F07). All figures come from {@link MortgageCalculator}; the
 * agent only describes them.
 *
 * <p>Parameters: {@value #LISTING_ID} (required); {@value #DOWN_PAYMENT}, {@value #RATE}
 * (percent) and {@value #YEARS} (optional, defaulting to 20% down, 5%, 25 years);
 * {@value #PROFILE} (FinancialProfile, optional). Data returned: {@value #MORTGAGE} and, with a
 * profile, {@value #AFFORDABILITY}.
 */
public class MortgageTool implements AgentTool {

    public static final String NAME = "mortgage";
    public static final String LISTING_ID = "listingId";
    public static final String DOWN_PAYMENT = "downPayment";
    public static final String RATE = "annualRate";
    public static final String YEARS = "years";
    public static final String PROFILE = "profile";
    public static final String MORTGAGE = "mortgage";
    public static final String AFFORDABILITY = "affordability";

    private final ListingRepository repository;
    private final MortgageCalculator calculator;

    public MortgageTool(ListingRepository repository, MortgageCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "monthly payment, total interest and affordability for ONE listing; params: "
                + "listingId (required), downPayment, annualRate (percent), years";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Optional<String> id = request.getString(LISTING_ID);
        if (id.isEmpty()) {
            return ToolResult.failure("Which property? A listing ID is needed for a mortgage calculation.");
        }
        Optional<Listing> found = repository.findById(id.get());
        if (found.isEmpty()) {
            return ToolResult.failure("No listing with ID " + id.get());
        }
        Listing listing = found.get();
        Optional<FinancialProfile> profile = request.get(PROFILE, FinancialProfile.class);

        MortgageTerms defaults = MortgageTerms.defaultsFor(listing.getPrice());
        double down = request.getDouble(DOWN_PAYMENT)
                .orElse(profile.map(FinancialProfile::getDownPayment).orElse(defaults.getDownPayment()));
        MortgageTerms terms = new MortgageTerms(down,
                request.getDouble(RATE).orElse(defaults.getAnnualRate()),
                request.getInt(YEARS).orElse(defaults.getYears()));

        Map<String, Object> data = new LinkedHashMap<>();
        MortgageResult mortgage = calculator.calculate(listing.getPrice(), terms);
        data.put(MORTGAGE, mortgage);
        data.put("terms", terms);
        if (profile.isPresent()) {
            AffordabilityResult affordability = calculator.assess(listing, profile.get());
            data.put(AFFORDABILITY, affordability);
        }
        return ToolResult.success(List.of(listing), data);
    }
}
