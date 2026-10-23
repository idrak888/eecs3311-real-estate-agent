package ca.yorku.realestate.persistence;

import java.util.List;

/**
 * Outcome of importing a listing file: how many records loaded and why the others did not.
 * Shown in the GUI status bar and printed by the CLI at startup.
 */
public record LoadSummary(String fileName, int loaded, List<String> rejections) {

    public LoadSummary {
        rejections = List.copyOf(rejections);
    }

    public int rejected() {
        return rejections.size();
    }

    /** One-line summary, e.g. {@code "Loaded 58 listings from listings.json (2 rejected)"}. */
    public String describe() {
        String base = "Loaded " + loaded + (loaded == 1 ? " listing" : " listings")
                + " from " + fileName;
        return rejected() == 0 ? base : base + " (" + rejected() + " rejected)";
    }
}
