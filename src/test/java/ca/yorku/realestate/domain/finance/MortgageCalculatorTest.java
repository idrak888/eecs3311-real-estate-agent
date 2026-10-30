package ca.yorku.realestate.domain.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MortgageCalculatorTest {

    private final MortgageCalculator calc = new MortgageCalculator();

    @Test
    void standardPaymentMatchesAmortizationFormula() {
        // $400,000 at 6% for 30 years: well-known reference payment $2,398.20
        MortgageTerms terms = new MortgageTerms(100_000, 6.0, 30);
        assertEquals(2398.20, calc.monthlyPayment(500_000, terms), 0.001);
    }

    @Test
    void totalInterestIsPaymentsMinusPrincipal() {
        MortgageTerms terms = new MortgageTerms(100_000, 6.0, 30);
        // 2398.20 * 360 - 400000
        assertEquals(463_352.00, calc.totalInterest(500_000, terms), 0.001);
    }

    @Test
    void calculateReturnsAllF06Outputs() {
        MortgageResult r = calc.calculate(850_000, new MortgageTerms(170_000, 4.8, 25));
        assertEquals(680_000, r.getPrincipal(), 1e-9);
        assertEquals(0.8, r.getLoanToValue(), 1e-9);
        assertTrue(r.isFinancingRequired());
        assertEquals(3896.38, r.getMonthlyPayment(), 0.001);
        assertEquals(Math.round((3896.38 * 300 - 680_000) * 100) / 100.0, r.getTotalInterest(), 0.001);
        assertEquals(850_000 + r.getTotalInterest(), r.getTotalCost(), 0.001);
    }

    @Test
    void zeroRateIsPrincipalOverMonths() {
        MortgageTerms terms = new MortgageTerms(0, 0.0, 25);
        assertEquals(1000.00, calc.monthlyPayment(300_000, terms), 1e-9);
        assertEquals(0.0, calc.totalInterest(300_000, terms), 1e-9);
    }

    @Test
    void downPaymentEqualToPriceNeedsNoFinancing() {
        MortgageResult r = calc.calculate(500_000, new MortgageTerms(500_000, 5.0, 25));
        assertFalse(r.isFinancingRequired());
        assertEquals(0.0, r.getMonthlyPayment(), 1e-9);
        assertEquals(0.0, r.getTotalInterest(), 1e-9);
        assertEquals(500_000, r.getTotalCost(), 1e-9);
        assertEquals(0.0, r.getLoanToValue(), 1e-9);
    }

    @Test
    void downPaymentAbovePriceGivesZeroNotNegativePrincipal() {
        MortgageResult r = calc.calculate(400_000, new MortgageTerms(450_000, 5.0, 25));
        assertEquals(0.0, r.getPrincipal(), 1e-9);
        assertEquals(0.0, r.getMonthlyPayment(), 1e-9);
    }

    @Test
    void paymentIsRoundedToCents() {
        double p = calc.monthlyPayment(333_333, new MortgageTerms(0, 3.33, 17));
        assertEquals(p, Math.round(p * 100) / 100.0, 1e-12);
    }

    @Test
    void zeroPriceIsZeroPayment() {
        assertEquals(0.0, calc.monthlyPayment(0, MortgageTerms.defaultsFor(0)), 1e-9);
    }

    @Test
    void longerAmortizationLowersPaymentButRaisesInterest() {
        MortgageTerms short15 = new MortgageTerms(0, 5.0, 15);
        MortgageTerms long30 = new MortgageTerms(0, 5.0, 30);
        assertTrue(calc.monthlyPayment(500_000, long30) < calc.monthlyPayment(500_000, short15));
        assertTrue(calc.totalInterest(500_000, long30) > calc.totalInterest(500_000, short15));
    }

    @Test
    void rejectsNegativeOrNonFinitePrice() {
        MortgageTerms t = MortgageTerms.defaultsFor(1);
        assertThrows(IllegalArgumentException.class, () -> calc.monthlyPayment(-1, t));
        assertThrows(IllegalArgumentException.class, () -> calc.monthlyPayment(Double.NaN, t));
        assertThrows(IllegalArgumentException.class, () -> calc.calculate(1, null));
    }

    // ---- MortgageTerms ----

    @Test
    void termsRejectInvalidInputAtBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new MortgageTerms(-1, 5, 25));
        assertThrows(IllegalArgumentException.class, () -> new MortgageTerms(0, -0.1, 25));
        assertThrows(IllegalArgumentException.class, () -> new MortgageTerms(0, 480, 25));
        assertThrows(IllegalArgumentException.class, () -> new MortgageTerms(0, Double.NaN, 25));
        assertThrows(IllegalArgumentException.class, () -> new MortgageTerms(0, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new MortgageTerms(0, 5, 41));
    }

    @Test
    void defaultTermsAreTwentyPercentDownFivePercentTwentyFiveYears() {
        MortgageTerms t = MortgageTerms.defaultsFor(850_000);
        assertEquals(170_000, t.getDownPayment(), 1e-9);
        assertEquals(5.0, t.getAnnualRate(), 1e-9);
        assertEquals(25, t.getYears());
        assertEquals(300, t.getNumberOfPayments());
    }

    @Test
    void monthlyRateConvertsPercent() {
        assertEquals(0.004, new MortgageTerms(0, 4.8, 25).getMonthlyRate(), 1e-12);
    }

    @Test
    void withDownPaymentKeepsRateAndYears() {
        MortgageTerms t = new MortgageTerms(1, 4.8, 20).withDownPayment(50_000);
        assertEquals(50_000, t.getDownPayment(), 1e-9);
        assertEquals(4.8, t.getAnnualRate(), 1e-9);
        assertEquals(20, t.getYears());
    }
}
