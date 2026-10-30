package ca.yorku.realestate.domain.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rounding helpers for currency figures shown to the user. */
final class Money {

    private Money() {
    }

    /** Rounds to the nearest cent, halves up. */
    static double cents(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
