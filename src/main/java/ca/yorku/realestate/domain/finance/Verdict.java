package ca.yorku.realestate.domain.finance;

/** Outcome of an affordability assessment (F07). */
public enum Verdict {
    AFFORDABLE("Affordable"),
    MARGINAL("Marginal"),
    UNAFFORDABLE("Unaffordable");

    private final String label;

    Verdict(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
