package ca.yorku.realestate.domain.finance;

/**
 * Output of an affordability assessment (F07).
 *
 * <p>Ratios are fractions of gross monthly income (0.32 = 32%). With zero income they are
 * {@link Double#NaN}, since no ratio can be computed; check {@link #hasRatios()} before
 * displaying them.
 */
public final class AffordabilityResult {

    private final Verdict verdict;
    private final double gdsRatio;
    private final double tdsRatio;
    private final double maxPrice;
    private final double monthlyHousingCost;
    private final boolean debtLimited;
    private final String explanation;

    AffordabilityResult(Verdict verdict, double gdsRatio, double tdsRatio, double maxPrice,
                        double monthlyHousingCost, boolean debtLimited, String explanation) {
        this.verdict = verdict;
        this.gdsRatio = gdsRatio;
        this.tdsRatio = tdsRatio;
        this.maxPrice = maxPrice;
        this.monthlyHousingCost = monthlyHousingCost;
        this.debtLimited = debtLimited;
        this.explanation = explanation;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    /** Gross debt service: housing cost over gross monthly income. */
    public double getGdsRatio() {
        return gdsRatio;
    }

    /** Total debt service: housing cost plus existing debt over gross monthly income. */
    public double getTdsRatio() {
        return tdsRatio;
    }

    public boolean hasRatios() {
        return !Double.isNaN(gdsRatio);
    }

    /** Highest price this profile supports at the qualifying thresholds, whole dollars. */
    public double getMaxPrice() {
        return maxPrice;
    }

    /** Mortgage payment plus estimated property tax and heating. */
    public double getMonthlyHousingCost() {
        return monthlyHousingCost;
    }

    /** True when existing debt alone exceeds the TDS limit, so no price is affordable. */
    public boolean isDebtLimited() {
        return debtLimited;
    }

    /** One-sentence reason for the verdict, suitable for the GUI or CLI. */
    public String getExplanation() {
        return explanation;
    }
}
