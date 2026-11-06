package ca.yorku.realestate.domain;

import ca.yorku.realestate.domain.filter.Filter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Explains an empty search deterministically (F02, F03 alternative flows).
 *
 * <p>Each active requirement is dropped in turn and the remaining criteria re-run against the
 * listings. The requirement whose removal finds the most matches is the most restrictive one.
 * This runs in code, not in the model, so the advice ("without a garage there are 3 matches")
 * is always true for the loaded data.
 */
public final class ZeroMatchAnalyzer {

    /** One requirement and how many listings match without it. */
    public record Relaxation(String requirement, SearchCriteria relaxed, int matches) {
    }

    /** Result of analyzing an empty search. */
    public record Diagnosis(List<Relaxation> relaxations) {

        public Diagnosis {
            relaxations = List.copyOf(relaxations);
        }

        /** The requirement whose removal finds the most listings, if any removal finds one. */
        public Optional<Relaxation> mostRestrictive() {
            return relaxations.isEmpty() || relaxations.get(0).matches() == 0
                    ? Optional.empty() : Optional.of(relaxations.get(0));
        }

        /** One or two sentences for the user. */
        public String message() {
            Optional<Relaxation> best = mostRestrictive();
            if (relaxations.isEmpty()) {
                return "No listings were loaded.";
            }
            if (best.isEmpty()) {
                return "No single change finds a match; try relaxing two requirements, such as price and area.";
            }
            Relaxation r = best.get();
            StringBuilder sb = new StringBuilder("The most restrictive requirement is ")
                    .append(r.requirement()).append(": without it, ").append(r.matches())
                    .append(r.matches() == 1 ? " listing matches." : " listings match.");
            List<String> others = relaxations.stream().skip(1).filter(x -> x.matches() > 0).limit(2)
                    .map(x -> x.requirement() + " (" + x.matches() + ")").collect(Collectors.toList());
            if (!others.isEmpty()) {
                sb.append(" Also worth relaxing: ").append(String.join(", ", others)).append('.');
            }
            return sb.toString();
        }
    }

    private ZeroMatchAnalyzer() {
    }

    /**
     * Drops each requirement in turn and counts matches.
     *
     * @return relaxations sorted by matches, most first; ties keep requirement order
     * @throws IllegalArgumentException if the criteria are invalid
     */
    public static Diagnosis analyze(SearchCriteria criteria, List<Listing> listings) {
        if (!criteria.isValid()) {
            throw new IllegalArgumentException(String.join("; ", criteria.validationErrors()));
        }
        List<Relaxation> out = new ArrayList<>();
        if (listings.isEmpty()) {
            return new Diagnosis(out);
        }
        if (criteria.getMinPrice() != null) {
            add(out, "a minimum price of " + money(criteria.getMinPrice()),
                    SearchCriteriaBuilder.from(criteria).withMinPrice(null).build(), listings);
        }
        if (criteria.getMaxPrice() != null) {
            add(out, "a maximum price of " + money(criteria.getMaxPrice()),
                    SearchCriteriaBuilder.from(criteria).withMaxPrice(null).build(), listings);
        }
        if (criteria.getMinBedrooms() != null) {
            add(out, "at least " + criteria.getMinBedrooms() + " bedrooms",
                    SearchCriteriaBuilder.from(criteria).withMinBedrooms(null).build(), listings);
        }
        if (criteria.getMinBathrooms() != null) {
            add(out, "at least " + criteria.getMinBathrooms() + " bathrooms",
                    SearchCriteriaBuilder.from(criteria).withMinBathrooms(null).build(), listings);
        }
        if (!criteria.getTypes().isEmpty()) {
            add(out, "type " + criteria.getTypes().stream().map(t -> t.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" or ")),
                    SearchCriteriaBuilder.from(criteria).clearTypes().build(), listings);
        }
        if (!criteria.getNeighborhoods().isEmpty()) {
            add(out, "the area " + String.join(" or ", criteria.getNeighborhoods()),
                    SearchCriteriaBuilder.from(criteria).clearNeighborhoods().build(), listings);
        }
        for (String feature : criteria.getRequiredFeatures()) {
            SearchCriteriaBuilder b = SearchCriteriaBuilder.from(criteria).clearFeatures();
            criteria.getRequiredFeatures().stream().filter(f -> !f.equals(feature)).forEach(b::withFeature);
            add(out, "the feature \"" + feature + "\"", b.build(), listings);
        }
        out.sort(Comparator.comparingInt(Relaxation::matches).reversed());
        return new Diagnosis(out);
    }

    private static void add(List<Relaxation> out, String label, SearchCriteria relaxed, List<Listing> listings) {
        Filter f = relaxed.toFilter();
        int n = (int) listings.stream().filter(f::matches).count();
        out.add(new Relaxation(label, relaxed, n));
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "$%,.0f", v);
    }
}
