package ca.yorku.realestate.domain.finance;

/**
 * Deterministic mortgage arithmetic (F06).
 *
 * <p>Uses the standard amortization formula with a monthly rate of annual rate / 12:
 * {@code M = P * r / (1 - (1 + r)^-n)}. A zero rate is special-cased, since the formula
 * divides by zero there, and the payment becomes principal over the number of months.
 * (Canadian lenders compound semi-annually, which gives a slightly lower payment; the
 * standard monthly formula is used here for transparency and is applied consistently.)
 */
public class MortgageCalculator {

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
