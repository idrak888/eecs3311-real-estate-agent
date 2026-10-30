package ca.yorku.realestate.domain.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import org.junit.jupiter.api.Test;

class AffordabilityTest {

    private final MortgageCalculator calc = new MortgageCalculator();

    private static Listing priced(double price) {
        return new Listing("P" + (long) price, price, 3, 2, 1500, PropertyType.HOUSE, "Maple", 2010, null);
    }

    @Test
    void housingCostIsPaymentPlusTaxPlusHeating() {
        // payment on 680k at 5%/25y = 4075.37; tax 850k * 1% / 12 = 708.33; heating 100
        assertEquals(4783.54, calc.monthlyHousingCost(850_000, 170_000), 0.001);
    }

    @Test
    void comfortableRatiosAreAffordable() {
        AffordabilityResult r = calc.assess(priced(850_000), new FinancialProfile(180_000, 500, 170_000));
        assertEquals(Verdict.AFFORDABLE, r.getVerdict());
        assertEquals(4783.54 / 15_000, r.getGdsRatio(), 1e-9);
        assertEquals((4783.54 + 500) / 15_000, r.getTdsRatio(), 1e-9);
        assertTrue(r.hasRatios());
        assertFalse(r.isDebtLimited());
        assertTrue(r.getExplanation().startsWith("GDS 31.9% and TDS 35.2%"));
    }

    @Test
    void ratiosJustUnderLimitAreMarginal() {
        // income 120k, 100k down, 640k: GDS 37.9%, inside the 3-point band below 39%
        AffordabilityResult r = calc.assess(priced(640_000), new FinancialProfile(120_000, 0, 100_000));
        assertEquals(Verdict.MARGINAL, r.getVerdict());
        assertEquals(0.379012, r.getGdsRatio(), 1e-6);
    }

    @Test
    void ratioOverLimitIsUnaffordable() {
        AffordabilityResult r = calc.assess(priced(900_000), new FinancialProfile(120_000, 0, 100_000));
        assertEquals(Verdict.UNAFFORDABLE, r.getVerdict());
        assertTrue(r.getExplanation().contains("exceeds the 39.0% limit"));
    }

    @Test
    void tdsCanFailWhenGdsPasses() {
        // 600k with 100k down on 150k income: GDS 28.2%; 2,500/month debt pushes TDS to 48.2%
        AffordabilityResult r = calc.assess(priced(600_000), new FinancialProfile(150_000, 2_500, 100_000));
        assertTrue(r.getGdsRatio() < MortgageCalculator.GDS_LIMIT);
        assertEquals(Verdict.UNAFFORDABLE, r.getVerdict());
        assertTrue(r.getExplanation().startsWith("TDS"));
    }

    @Test
    void zeroIncomeIsUnaffordableWithoutDivision() {
        AffordabilityResult r = calc.assess(priced(500_000), new FinancialProfile(0, 0, 100_000));
        assertEquals(Verdict.UNAFFORDABLE, r.getVerdict());
        assertFalse(r.hasRatios());
        assertEquals(0.0, r.getMaxPrice(), 1e-9);
        assertTrue(r.getExplanation().contains("No income"));
    }

    @Test
    void debtAloneOverLimitIsFlaggedSpecifically() {
        // 4,000/month debt on 100k income is 48% before any housing cost
        AffordabilityResult r = calc.assess(priced(1_000), new FinancialProfile(100_000, 4_000, 0));
        assertEquals(Verdict.UNAFFORDABLE, r.getVerdict());
        assertTrue(r.isDebtLimited());
        assertEquals(0.0, r.getMaxPrice(), 1e-9);
        assertTrue(r.getExplanation().contains("Existing debt alone is 48.0%"));
    }

    @Test
    void maxSupportablePriceInvertsTheCalculation() {
        FinancialProfile p = new FinancialProfile(150_000, 0, 100_000);
        assertEquals(802_425, calc.maxSupportablePrice(p), 1e-9);
    }

    @Test
    void maxPriceSitsExactlyAtTheLimit() {
        FinancialProfile p = new FinancialProfile(150_000, 750, 100_000);
        double max = calc.maxSupportablePrice(p);
        AffordabilityResult atMax = calc.assess(priced(max), p);
        AffordabilityResult above = calc.assess(priced(max + 2_000), p);
        assertTrue(atMax.getVerdict() != Verdict.UNAFFORDABLE);
        assertEquals(Verdict.UNAFFORDABLE, above.getVerdict());
    }

    @Test
    void moreDebtLowersMaxPrice() {
        double none = calc.maxSupportablePrice(new FinancialProfile(150_000, 0, 100_000));
        double some = calc.maxSupportablePrice(new FinancialProfile(150_000, 1_500, 100_000));
        assertTrue(some < none);
    }

    @Test
    void largeDownPaymentCoveringPriceUsesTaxAndHeatingOnly() {
        // 3k/month income, all-cash buyer with 2M: allowed housing 1,170 - 100 heating = 1,070
        // tax-only price = 1,070 / (1% / 12) = 1,284,000, which the 2M down payment covers
        FinancialProfile p = new FinancialProfile(36_000, 0, 2_000_000);
        assertEquals(1_284_000, calc.maxSupportablePrice(p), 1.0);
    }

    @Test
    void assessReportsMaxPriceAndHousingCost() {
        AffordabilityResult r = calc.assess(priced(600_000), new FinancialProfile(150_000, 0, 100_000));
        assertEquals(802_425, r.getMaxPrice(), 1e-9);
        assertEquals(3522.95, r.getMonthlyHousingCost(), 0.001);
    }

    @Test
    void customQualifyingRateRaisesCosts() {
        MortgageCalculator stressed = new MortgageCalculator(7.0, 25);
        FinancialProfile p = new FinancialProfile(150_000, 0, 100_000);
        assertTrue(stressed.maxSupportablePrice(p) < calc.maxSupportablePrice(p));
        assertThrows(IllegalArgumentException.class, () -> new MortgageCalculator(-1, 25));
    }

    @Test
    void assessRequiresListingAndProfile() {
        FinancialProfile p = new FinancialProfile(1, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> calc.assess(null, p));
        assertThrows(IllegalArgumentException.class, () -> calc.assess(priced(1), null));
        assertThrows(IllegalArgumentException.class, () -> calc.maxSupportablePrice(null));
    }

    @Test
    void profileRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> new FinancialProfile(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new FinancialProfile(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FinancialProfile(0, 0, Double.NaN));
    }

    @Test
    void profileMonthlyIncomeIsAnnualOverTwelve() {
        assertEquals(10_000, new FinancialProfile(120_000, 0, 0).getMonthlyIncome(), 1e-9);
    }

    @Test
    void verdictsHaveLabels() {
        assertEquals("Marginal", Verdict.MARGINAL.getLabel());
    }
}
