package ca.yorku.realestate.ui;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.ranking.BestMatchStrategy;
import ca.yorku.realestate.domain.ranking.PriceAscendingStrategy;
import ca.yorku.realestate.domain.ranking.PriceDescendingStrategy;
import ca.yorku.realestate.domain.ranking.RankingStrategy;
import ca.yorku.realestate.domain.ranking.ValuePerSqFtStrategy;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Formatting and parsing shared by the GUI and CLI, so both show identical values.
 * No business logic lives here.
 */
public final class Formats {

    private Formats() {
    }

    public static String money(double amount) {
        return String.format(Locale.ROOT, "$%,.0f", amount);
    }

    public static String moneyCents(double amount) {
        return String.format(Locale.ROOT, "$%,.2f", amount);
    }

    public static String percent(double fraction) {
        return Double.isNaN(fraction) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }

    public static String type(Listing l) {
        String t = l.getType().name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    public static String squareFeet(Listing l) {
        return l.hasSquareFeet() ? String.format(Locale.ROOT, "%,d", l.getSquareFeet()) : "unavailable";
    }

    public static String pricePerSqFt(Listing l) {
        return l.hasSquareFeet() ? money(l.getPricePerSqFt()) : "unavailable";
    }

    /**
     * Parses an amount such as {@code 850000}, {@code 850,000}, {@code $850k} or {@code 1.2M}.
     *
     * @throws IllegalArgumentException if the text is not an amount
     */
    public static double parseMoney(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("An amount is required");
        }
        String t = text.trim().toLowerCase(Locale.ROOT).replace("$", "").replace(",", "");
        double multiplier = 1;
        if (t.endsWith("k")) {
            multiplier = 1_000;
            t = t.substring(0, t.length() - 1);
        } else if (t.endsWith("m")) {
            multiplier = 1_000_000;
            t = t.substring(0, t.length() - 1);
        }
        try {
            double v = Double.parseDouble(t) * multiplier;
            if (!Double.isFinite(v) || v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not an amount: '" + text + "'");
        }
    }

    /** Ranking strategies by CLI key, in GUI dropdown order. */
    public static Map<String, RankingStrategy> strategies() {
        Map<String, RankingStrategy> m = new LinkedHashMap<>();
        m.put("best", new BestMatchStrategy());
        m.put("price-asc", new PriceAscendingStrategy());
        m.put("price-desc", new PriceDescendingStrategy());
        m.put("value", new ValuePerSqFtStrategy());
        return m;
    }

    public static Optional<RankingStrategy> strategy(String key) {
        return Optional.ofNullable(key == null ? null : strategies().get(key.trim().toLowerCase(Locale.ROOT)));
    }
}
