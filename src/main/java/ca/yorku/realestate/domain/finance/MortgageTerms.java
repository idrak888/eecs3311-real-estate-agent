package ca.yorku.realestate.domain.finance;

/**
 * Financing terms for one mortgage calculation (F06). Immutable and validated on construction,
 * so negative or non-numeric input is rejected at the boundary.
 *
 * <p>The annual rate is a percentage, e.g. {@code 4.8} for 4.8%.
 */
public final class MortgageTerms {

    /** Default annual rate, percent, used when the user does not specify one. */
    public static final double DEFAULT_ANNUAL_RATE = 5.0;
    /** Default amortization period in years. */
    public static final int DEFAULT_YEARS = 25;
    /** Default down payment as a fraction of price. */
    public static final double DEFAULT_DOWN_FRACTION = 0.20;

    /** Largest amortization accepted, in years. */
    public static final int MAX_YEARS = 40;
    /** Largest annual rate accepted, percent. Guards against entering 480 for 4.8%. */
    public static final double MAX_ANNUAL_RATE = 30.0;

    private final double downPayment;
    private final double annualRate;
    private final int years;

    /**
     * @throws IllegalArgumentException if any value is negative, not finite, or out of range
     */
    public MortgageTerms(double downPayment, double annualRate, int years) {
        if (!Double.isFinite(downPayment) || downPayment < 0) {
            throw new IllegalArgumentException("Down payment must be a non-negative amount");
        }
        if (!Double.isFinite(annualRate) || annualRate < 0 || annualRate > MAX_ANNUAL_RATE) {
            throw new IllegalArgumentException("Annual rate must be between 0% and "
                    + MAX_ANNUAL_RATE + "% (was " + annualRate + ")");
        }
        if (years < 1 || years > MAX_YEARS) {
            throw new IllegalArgumentException("Amortization must be between 1 and " + MAX_YEARS
                    + " years (was " + years + ")");
        }
        this.downPayment = downPayment;
        this.annualRate = annualRate;
        this.years = years;
    }

    /** Default terms for a price: 20% down, {@value #DEFAULT_ANNUAL_RATE}%, 25 years. Used by F05. */
    public static MortgageTerms defaultsFor(double price) {
        return new MortgageTerms(Math.max(0, price) * DEFAULT_DOWN_FRACTION, DEFAULT_ANNUAL_RATE,
                DEFAULT_YEARS);
    }

    public double getDownPayment() {
        return downPayment;
    }

    /** Annual rate in percent. */
    public double getAnnualRate() {
        return annualRate;
    }

    public int getYears() {
        return years;
    }

    public int getNumberOfPayments() {
        return years * 12;
    }

    /** Monthly rate as a fraction, e.g. 0.004 for 4.8% a year. */
    public double getMonthlyRate() {
        return annualRate / 100.0 / 12.0;
    }

    public MortgageTerms withDownPayment(double newDownPayment) {
        return new MortgageTerms(newDownPayment, annualRate, years);
    }
}
