package ca.yorku.realestate.domain.finance;

import ca.yorku.realestate.domain.Listing;
import java.util.Locale;

/**
 * Deterministic mortgage and affordability arithmetic (F06, F07).
 *
 * <p>Payments use the standard amortization formula with a monthly rate of annual rate / 12:
 * {@code M = P * r / (1 - (1 + r)^-n)}. A zero rate is special-cased, since the formula
 * divides by zero there, and the payment becomes principal over the number of months.
 * (Canadian lenders compound semi-annually, which gives a slightly lower payment; the
 * standard monthly formula is used here for transparency and is applied consistently.)
 *
 * <p>Affordability follows the Canadian debt service tests. Housing cost is the mortgage
 * payment plus estimated property tax and heating. The gross debt service ratio (GDS) is
 * housing cost over gross monthly income and must not exceed 39%; the total debt service
 * ratio (TDS) adds existing debt and must not exceed 44%. Within
 * {@value #MARGINAL_BAND_POINTS} percentage points below either limit the verdict is marginal.
 */
public class MortgageCalculator {

    /** Maximum gross debt service ratio. */
    public static final double GDS_LIMIT = 0.39;
    /** Maximum total debt service ratio. */
    public static final double TDS_LIMIT = 0.44;
    /** Width of the marginal band below each limit, in percentage points. */
    public static final double MARGINAL_BAND_POINTS = 3.0;
    /** Estimated annual property tax as a fraction of price. */
    public static final double PROPERTY_TAX_RATE = 0.01;
    /** Estimated monthly heating cost. */
    public static final double MONTHLY_HEATING = 100.0;

    private final double qualifyingRate;
    private final int qualifyingYears;

    /** Assesses affordability at the default rate and amortization. */
    public MortgageCalculator() {
        this(MortgageTerms.DEFAULT_ANNUAL_RATE, MortgageTerms.DEFAULT_YEARS);
    }

    /**
     * @param qualifyingRate annual rate, percent, used for affordability
     * @param qualifyingYears amortization used for affordability
     */
    public MortgageCalculator(double qualifyingRate, int qualifyingYears) {
        // Validates through MortgageTerms so the same bounds apply.
        new MortgageTerms(0, qualifyingRate, qualifyingYears);
        this.qualifyingRate = qualifyingRate;
        this.qualifyingYears = qualifyingYears;
    }

    // ---- F06 ----

    /**
     * Monthly principal and interest, rounded to cents. Zero when the down payment covers the
     * price.
     *
     * @throws IllegalArgumentException if price is negative or not finite
     */
    public double monthlyPayment(double price, MortgageTerms terms) {
        return Money.cents(rawPayment(principal(price, terms), terms));
    }

    /** Total interest over the full amortization, based on the rounded monthly payment. */
    public double totalInterest(double price, MortgageTerms terms) {
        double principal = principal(price, terms);
        if (principal == 0) {
            return 0;
        }
        double payment = Money.cents(rawPayment(principal, terms));
        return Money.cents(Math.max(0, payment * terms.getNumberOfPayments() - principal));
    }

    /** All F06 outputs for one price and set of terms. */
    public MortgageResult calculate(double price, MortgageTerms terms) {
        double principal = principal(price, terms);
        return new MortgageResult(price, principal, monthlyPayment(price, terms),
                totalInterest(price, terms));
    }

    // ---- F07 ----

    /**
     * Assesses whether the listing is affordable for the profile, at this calculator's
     * qualifying rate and amortization and the profile's down payment.
     *
     * @throws IllegalArgumentException if the listing or profile is null
     */
    public AffordabilityResult assess(Listing listing, FinancialProfile profile) {
        if (listing == null || profile == null) {
            throw new IllegalArgumentException("A listing and a financial profile are required");
        }
        double price = listing.getPrice();
        double housing = monthlyHousingCost(price, profile.getDownPayment());
        double maxPrice = maxSupportablePrice(profile);

        if (profile.getAnnualIncome() == 0) {
            return new AffordabilityResult(Verdict.UNAFFORDABLE, Double.NaN, Double.NaN, 0,
                    housing, false, "No income was entered, so no mortgage can be supported.");
        }

        double income = profile.getMonthlyIncome();
        double gds = housing / income;
        double tds = (housing + profile.getMonthlyDebt()) / income;
        boolean debtLimited = profile.getMonthlyDebt() / income > TDS_LIMIT;

        Verdict verdict;
        String explanation;
        if (debtLimited) {
            verdict = Verdict.UNAFFORDABLE;
            explanation = String.format(Locale.ROOT, "Existing debt alone is %s of income, above "
                    + "the %s TDS limit; no price is affordable until debt is reduced.",
                    percent(profile.getMonthlyDebt() / income), percent(TDS_LIMIT));
        } else if (gds > GDS_LIMIT || tds > TDS_LIMIT) {
            verdict = Verdict.UNAFFORDABLE;
            explanation = String.format(Locale.ROOT, "%s exceeds the %s limit.",
                    gds > GDS_LIMIT ? "GDS of " + percent(gds) : "TDS of " + percent(tds),
                    percent(gds > GDS_LIMIT ? GDS_LIMIT : TDS_LIMIT));
        } else if (gds > GDS_LIMIT - band() || tds > TDS_LIMIT - band()) {
            verdict = Verdict.MARGINAL;
            explanation = String.format(Locale.ROOT, "Within the limits but close: GDS %s "
                    + "(limit %s), TDS %s (limit %s).", percent(gds), percent(GDS_LIMIT),
                    percent(tds), percent(TDS_LIMIT));
        } else {
            verdict = Verdict.AFFORDABLE;
            explanation = String.format(Locale.ROOT, "GDS %s and TDS %s are comfortably within "
                    + "the %s and %s limits.", percent(gds), percent(tds), percent(GDS_LIMIT),
                    percent(TDS_LIMIT));
        }
        return new AffordabilityResult(verdict, gds, tds, maxPrice, housing, debtLimited, explanation);
    }

    /**
     * Highest price the profile supports at the GDS and TDS limits, found by inverting the
     * housing cost calculation. Rounded down to whole dollars. Zero with no income or when
     * existing debt alone exceeds the TDS limit.
     */
    public double maxSupportablePrice(FinancialProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("A financial profile is required");
        }
        double income = profile.getMonthlyIncome();
        double allowedHousing = Math.min(GDS_LIMIT * income, TDS_LIMIT * income - profile.getMonthlyDebt());
        double budget = allowedHousing - MONTHLY_HEATING;
        if (budget <= 0) {
            return 0;
        }
        double down = profile.getDownPayment();
        double tax = PROPERTY_TAX_RATE / 12.0;
        double k = rawPayment(1.0, qualifyingTerms(0));

        // If the down payment covers the price, housing cost is tax + heating only.
        double price;
        if (budget <= tax * down) {
            price = budget / tax;
        } else {
            // housing = k * (price - down) + tax * price + heating
            price = (budget + k * down) / (k + tax);
        }
        return Math.floor(price);
    }

    /** Mortgage payment plus estimated property tax and heating, rounded to cents. */
    public double monthlyHousingCost(double price, double downPayment) {
        double payment = monthlyPayment(price, qualifyingTerms(downPayment));
        return Money.cents(payment + price * PROPERTY_TAX_RATE / 12.0 + MONTHLY_HEATING);
    }

    private MortgageTerms qualifyingTerms(double downPayment) {
        return new MortgageTerms(downPayment, qualifyingRate, qualifyingYears);
    }

    private static double band() {
        return MARGINAL_BAND_POINTS / 100.0;
    }

    private static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }

    // ---- shared ----

    private static double principal(double price, MortgageTerms terms) {
        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException("Price must be a non-negative amount");
        }
        if (terms == null) {
            throw new IllegalArgumentException("Mortgage terms are required");
        }
        return Math.max(0, price - terms.getDownPayment());
    }

    private static double rawPayment(double principal, MortgageTerms terms) {
        if (principal == 0) {
            return 0;
        }
        int n = terms.getNumberOfPayments();
        double r = terms.getMonthlyRate();
        if (r == 0) {
            return principal / n;
        }
        return principal * r / (1 - Math.pow(1 + r, -n));
    }
}
