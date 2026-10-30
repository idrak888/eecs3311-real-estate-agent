package ca.yorku.realestate.domain.finance;

/**
 * The buyer's finances, entered once per session (F07). Immutable and validated.
 *
 * <p>Whether the user has entered a profile at all is the caller's concern: the service holds
 * an optional profile and prompts for one rather than assuming defaults.
 */
public final class FinancialProfile {

    private final double annualIncome;
    private final double monthlyDebt;
    private final double downPayment;

    /**
     * @param annualIncome gross household income per year
     * @param monthlyDebt existing debt payments per month (car, student loans, card minimums)
     * @param downPayment cash available for the down payment
     * @throws IllegalArgumentException if any value is negative or not finite
     */
    public FinancialProfile(double annualIncome, double monthlyDebt, double downPayment) {
        requireNonNegative(annualIncome, "Annual income");
        requireNonNegative(monthlyDebt, "Monthly debt");
        requireNonNegative(downPayment, "Down payment");
        this.annualIncome = annualIncome;
        this.monthlyDebt = monthlyDebt;
        this.downPayment = downPayment;
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a non-negative amount");
        }
    }

    public double getAnnualIncome() {
        return annualIncome;
    }

    public double getMonthlyIncome() {
        return annualIncome / 12.0;
    }

    public double getMonthlyDebt() {
        return monthlyDebt;
    }

    public double getDownPayment() {
        return downPayment;
    }
}
