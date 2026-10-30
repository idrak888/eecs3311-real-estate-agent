package ca.yorku.realestate.domain.finance;

/**
 * Output of a mortgage calculation (F06). Currency values are rounded to cents.
 *
 * <p>When the down payment covers the price, no financing is required: the payment and
 * interest are zero and {@link #isFinancingRequired()} is false.
 */
public final class MortgageResult {

    private final double price;
    private final double principal;
    private final double monthlyPayment;
    private final double totalInterest;
    private final double totalCost;
    private final double loanToValue;

    MortgageResult(double price, double principal, double monthlyPayment, double totalInterest) {
        this.price = price;
        this.principal = principal;
        this.monthlyPayment = monthlyPayment;
        this.totalInterest = totalInterest;
        this.totalCost = Money.cents(price + totalInterest);
        this.loanToValue = price > 0 ? principal / price : 0;
    }

    public double getPrice() {
        return price;
    }

    /** Amount borrowed: price minus down payment, never negative. */
    public double getPrincipal() {
        return principal;
    }

    /** Monthly principal and interest. */
    public double getMonthlyPayment() {
        return monthlyPayment;
    }

    /** Total paid over the amortization minus the principal. */
    public double getTotalInterest() {
        return totalInterest;
    }

    /** Price plus total interest. */
    public double getTotalCost() {
        return totalCost;
    }

    /** Principal over price, as a fraction (0.8 = 80%). */
    public double getLoanToValue() {
        return loanToValue;
    }

    public boolean isFinancingRequired() {
        return principal > 0;
    }
}
