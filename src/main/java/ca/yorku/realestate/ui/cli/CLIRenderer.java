package ca.yorku.realestate.ui.cli;

import ca.yorku.realestate.agent.AgentResponse;
import ca.yorku.realestate.agent.tool.ComparisonResult;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.domain.ranking.RankingStrategy;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import ca.yorku.realestate.persistence.LoadSummary;
import ca.yorku.realestate.service.InvalidCriteriaException;
import ca.yorku.realestate.service.NoSuchListingException;
import ca.yorku.realestate.service.RealEstateService;
import ca.yorku.realestate.service.SearchObserver;
import ca.yorku.realestate.ui.Formats;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Command-line frontend. Like the GUI, it is a thin layer over {@link RealEstateService}: it
 * parses commands, calls the facade, and prints what comes back. It observes the service only
 * for warnings (e.g. the shortlist could not be saved); results are printed by each command.
 */
public final class CLIRenderer implements SearchObserver {

    /** Results printed per search; the rest are summarized as a count. */
    static final int RESULTS_SHOWN = 10;

    private final RealEstateService service;
    private final BufferedReader in;
    private final PrintStream out;
    private boolean running;

    public CLIRenderer(RealEstateService service, BufferedReader in, PrintStream out) {
        this.service = service;
        this.in = in;
        this.out = out;
        service.addObserver(this);
    }

    /** Reads commands until {@code quit} or end of input. */
    public void run() throws IOException {
        running = true;
        out.println("Real Estate Advisor (CLI). Type 'help' for commands.");
        while (running) {
            out.print("> ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                break;
            }
            execute(line);
        }
        service.removeObserver(this);
    }

    /** Runs one command line. Public so tests and scripted demos can drive the CLI. */
    public void execute(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1].trim() : "";
        try {
            switch (command) {
                case "help", "?" -> help();
                case "search" -> search(rest);
                case "ask" -> ask(rest);
                case "show" -> show(rest);
                case "mortgage" -> mortgage(rest);
                case "profile" -> profile(rest);
                case "afford" -> afford(rest);
                case "compare" -> compare(rest);
                case "shortlist" -> shortlist(rest);
                case "undo" -> undo();
                case "sort" -> sort(rest);
                case "criteria" -> out.println("Criteria: " + describe(service.getCriteria()));
                case "load" -> load(rest);
                case "quit", "exit" -> {
                    running = false;
                    out.println("Goodbye.");
                }
                default -> out.println("Unknown command '" + parts[0] + "'. Type 'help' for commands, "
                        + "or 'ask <question>' to talk to the advisor.");
            }
        } catch (InvalidCriteriaException e) {
            out.println("Search not run: " + e.getMessage());
        } catch (NoSuchListingException | IllegalArgumentException | IllegalStateException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    // ---- commands ----

    private void help() {
        out.println(String.join("\n",
                "Commands:",
                "  search [--min-price N] [--max-price N] [--beds N] [--baths N]",
                "         [--type house|condo|townhouse]... [--area NAME]... [--feature NAME]... [--sort KEY]",
                "                             structured search; 'search' alone lists everything",
                "  ask <question>             talk to the AI advisor, e.g. ask 3 bed house under 900k",
                "  show <id>                  listing details with payment at default terms",
                "  mortgage <id> [--down N] [--rate PCT] [--years N]",
                "  profile <income> <monthly-debt> <down-payment>   set your finances; 'profile' shows them",
                "  afford <id>                affordability verdict using your profile",
                "  compare <id> <id> [<id> <id>]",
                "  shortlist [add <id> | remove <id>]",
                "  undo                       undo the last shortlist change",
                "  sort best|price-asc|price-desc|value",
                "  criteria                   show the active search criteria",
                "  load <file.json|file.csv>  load a different listing file",
                "  quit",
                "Amounts accept 850000, 850,000, 850k or 1.2M. Multi-word names use quotes: --area \"Liberty Village\""));
    }

    private void search(String args) {
        List<String> tokens = tokenize(args);
        SearchCriteriaBuilder b = new SearchCriteriaBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String flag = tokens.get(i).toLowerCase(Locale.ROOT);
            if (!flag.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected '" + tokens.get(i) + "'. Flags start with --; see 'help'.");
            }
            if (i + 1 >= tokens.size()) {
                throw new IllegalArgumentException(flag + " needs a value");
            }
            String value = tokens.get(++i);
            switch (flag) {
                case "--min-price" -> b.withMinPrice(Formats.parseMoney(value));
                case "--max-price" -> b.withMaxPrice(Formats.parseMoney(value));
                case "--beds" -> b.withMinBedrooms(parseCount(value, flag));
                case "--baths" -> b.withMinBathrooms(parseCount(value, flag));
                case "--type" -> b.withType(PropertyType.fromString(value));
                case "--area" -> b.withNeighborhood(value);
                case "--feature" -> b.withFeature(value);
                case "--sort" -> setStrategy(value);
                default -> throw new IllegalArgumentException("Unknown flag " + flag + "; see 'help'.");
            }
        }
        List<Listing> results = service.search(b.build());
        out.println("Criteria: " + describe(service.getCriteria()) + "  |  Ranked by: "
                + service.getRankingStrategy().getName());
        renderResults(results);
        service.diagnoseNoResults().ifPresent(d -> out.println(d.message()));
    }

    private void ask(String question) {
        if (question.isEmpty()) {
            throw new IllegalArgumentException("Usage: ask <question>");
        }
        out.println("(thinking...)");
        AgentResponse r = service.askAgent(question);
        printAgent(r);
    }

    private void printAgent(AgentResponse r) {
        out.println();
        out.println(r.getMessage());
        r.getComparison().ifPresent(c -> {
            out.println();
            out.print(c.toTable());
        });
        if (!r.getListings().isEmpty() && r.getComparison().isEmpty() && r.getToolsUsed().contains("search")) {
            out.println();
            renderResults(r.getListings());
        }
        r.getNotice().ifPresent(n -> out.println("Note: " + n));
        if (!r.isAiAvailable() && r.getComparison().isEmpty()) {
            out.println("Tip: 'search --max-price 900k --beds 3 --type house' works without the assistant.");
        }
        if (!r.getToolsUsed().isEmpty()) {
            out.println("[tools: " + String.join(", ", r.getToolsUsed()) + "]");
        }
    }

    private void show(String id) {
        Listing l = requireListing(id);
        out.println(l.getId() + " - " + Formats.type(l) + " in " + l.getNeighborhood());
        out.println("  Price:          " + Formats.money(l.getPrice()));
        out.println("  Bedrooms:       " + l.getBedrooms());
        out.println("  Bathrooms:      " + l.getBathrooms());
        out.println("  Square feet:    " + Formats.squareFeet(l));
        out.println("  Price/sq ft:    " + Formats.pricePerSqFt(l));
        out.println("  Year built:     " + l.getYearBuilt());
        out.println("  Features:       " + (l.getFeatures().isEmpty() ? "none" : String.join(", ", l.getFeatures())));
        MortgageResult m = service.calculateMortgage(l.getId(), null);
        out.println("  Est. payment:   " + Formats.moneyCents(m.getMonthlyPayment()) + "/month (20% down, "
                + MortgageTerms.DEFAULT_ANNUAL_RATE + "%, " + MortgageTerms.DEFAULT_YEARS + " years)");
        out.println("  Shortlisted:    " + (service.getShortlist().contains(l.getId()) ? "yes" : "no"));
    }

    private void mortgage(String args) {
        List<String> tokens = tokenize(args);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Usage: mortgage <id> [--down N] [--rate PCT] [--years N]");
        }
        Listing l = requireListing(tokens.get(0));
        MortgageTerms defaults = MortgageTerms.defaultsFor(l.getPrice());
        double down = defaults.getDownPayment();
        double rate = defaults.getAnnualRate();
        int years = defaults.getYears();
        for (int i = 1; i < tokens.size(); i++) {
            String flag = tokens.get(i).toLowerCase(Locale.ROOT);
            if (i + 1 >= tokens.size()) {
                throw new IllegalArgumentException(flag + " needs a value");
            }
            String value = tokens.get(++i);
            switch (flag) {
                case "--down" -> down = Formats.parseMoney(value);
                case "--rate" -> rate = parseNumber(value.replace("%", ""), flag);
                case "--years" -> years = parseCount(value, flag);
                default -> throw new IllegalArgumentException("Unknown flag " + flag);
            }
        }
        MortgageTerms terms = new MortgageTerms(down, rate, years);
        MortgageResult m = service.calculateMortgage(l.getId(), terms);
        out.println(l.getId() + " at " + Formats.money(l.getPrice()) + ", " + Formats.money(down) + " down, "
                + rate + "% over " + years + " years:");
        if (!m.isFinancingRequired()) {
            out.println("  No mortgage needed: the down payment covers the price.");
            return;
        }
        out.println("  Borrowed:         " + Formats.money(m.getPrincipal()) + "  (LTV " + Formats.percent(m.getLoanToValue()) + ")");
        out.println("  Monthly payment:  " + Formats.moneyCents(m.getMonthlyPayment()));
        out.println("  Total interest:   " + Formats.money(m.getTotalInterest()));
        out.println("  Total cost:       " + Formats.money(m.getTotalCost()));
    }

    private void profile(String args) {
        List<String> tokens = tokenize(args);
        if (tokens.isEmpty()) {
            Optional<FinancialProfile> p = service.getFinancialProfile();
            if (p.isEmpty()) {
                out.println("No financial profile yet. Usage: profile <annual-income> <monthly-debt> <down-payment>");
            } else {
                out.println("Income " + Formats.money(p.get().getAnnualIncome()) + "/year, debt "
                        + Formats.money(p.get().getMonthlyDebt()) + "/month, down payment "
                        + Formats.money(p.get().getDownPayment()));
            }
            return;
        }
        if (tokens.size() != 3) {
            throw new IllegalArgumentException("Usage: profile <annual-income> <monthly-debt> <down-payment>");
        }
        FinancialProfile p = new FinancialProfile(Formats.parseMoney(tokens.get(0)),
                Formats.parseMoney(tokens.get(1)), Formats.parseMoney(tokens.get(2)));
        service.setFinancialProfile(p);
        out.println("Profile saved. Max supportable price: about " + Formats.money(service.maxSupportablePrice()) + ".");
    }

    private void afford(String id) {
        Listing l = requireListing(id);
        if (service.getFinancialProfile().isEmpty()) {
            out.println("Set your finances first: profile <annual-income> <monthly-debt> <down-payment>");
            return;
        }
        AffordabilityResult a = service.assessAffordability(l.getId(), null);
        out.println(l.getId() + " at " + Formats.money(l.getPrice()) + ": " + a.getVerdict().getLabel().toUpperCase(Locale.ROOT));
        out.println("  " + a.getExplanation());
        out.println("  Monthly housing cost: " + Formats.moneyCents(a.getMonthlyHousingCost())
                + "  |  GDS " + Formats.percent(a.getGdsRatio()) + "  TDS " + Formats.percent(a.getTdsRatio()));
        out.println("  Max supportable price: " + Formats.money(a.getMaxPrice()));
    }

    private void compare(String args) {
        List<String> ids = tokenize(args);
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Usage: compare <id> <id> [<id> <id>] (two to four listings)");
        }
        printAgent(service.compare(ids));
    }

    private void shortlist(String args) {
        List<String> tokens = tokenize(args);
        if (tokens.isEmpty()) {
            List<Listing> items = service.getShortlistedListings();
            if (items.isEmpty()) {
                out.println("Shortlist is empty. Add with: shortlist add <id>");
            } else {
                out.println("Shortlist (" + items.size() + "):");
                renderResults(items);
            }
            return;
        }
        if (tokens.size() != 2) {
            throw new IllegalArgumentException("Usage: shortlist [add <id> | remove <id>]");
        }
        String id = tokens.get(1).toUpperCase(Locale.ROOT);
        switch (tokens.get(0).toLowerCase(Locale.ROOT)) {
            case "add" -> out.println(service.addToShortlist(id)
                    ? "Added " + id + ". Shortlist has " + service.getShortlist().size() + "."
                    : id + " is already on the shortlist.");
            case "remove" -> out.println(service.removeFromShortlist(id)
                    ? "Removed " + id + ". Shortlist has " + service.getShortlist().size() + "."
                    : id + " is not on the shortlist.");
            default -> throw new IllegalArgumentException("Usage: shortlist [add <id> | remove <id>]");
        }
    }

    private void undo() {
        Optional<String> undone = service.undo();
        out.println(undone.map(d -> "Undid: " + d + ".").orElse("Nothing to undo."));
    }

    private void sort(String key) {
        setStrategy(key);
        out.println("Ranked by: " + service.getRankingStrategy().getName());
        renderResults(service.getResults());
    }

    private void setStrategy(String key) {
        RankingStrategy s = Formats.strategy(key).orElseThrow(() -> new IllegalArgumentException(
                "Unknown sort '" + key + "'. Use one of: " + String.join(", ", Formats.strategies().keySet())));
        service.setRankingStrategy(s);
    }

    private void load(String path) {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Usage: load <file.json|file.csv>");
        }
        try {
            LoadSummary summary = service.loadListings(new File(unquote(path)));
            out.println(summary.describe());
            summary.rejections().forEach(r -> out.println("  rejected " + r));
        } catch (IOException e) {
            out.println("Error: " + e.getMessage());
        } catch (RuntimeException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    // ---- rendering ----

    /** Prints a results table, at most {@value #RESULTS_SHOWN} rows. */
    public void renderResults(List<Listing> results) {
        if (results.isEmpty()) {
            out.println("No listings match.");
            return;
        }
        out.println(String.format(Locale.ROOT, "%-6s %-10s %-16s %12s %4s %5s %11s",
                "ID", "Type", "Neighborhood", "Price", "Bed", "Bath", "Sq ft"));
        int shown = Math.min(RESULTS_SHOWN, results.size());
        for (Listing l : results.subList(0, shown)) {
            out.println(String.format(Locale.ROOT, "%-6s %-10s %-16s %12s %4d %5d %11s",
                    l.getId(), Formats.type(l), truncate(l.getNeighborhood(), 16), Formats.money(l.getPrice()),
                    l.getBedrooms(), l.getBathrooms(), Formats.squareFeet(l)));
        }
        if (results.size() > shown) {
            out.println("... and " + (results.size() - shown) + " more (" + results.size() + " total)");
        } else {
            out.println("(" + results.size() + (results.size() == 1 ? " listing)" : " listings)"));
        }
    }

    @Override
    public void onResultsChanged(List<Listing> results) {
        // Commands print their own results.
    }

    @Override
    public void onCriteriaChanged(SearchCriteria criteria) {
        // Shown on demand with 'criteria'.
    }

    @Override
    public void onShortlistChanged(Shortlist shortlist) {
        // Commands confirm their own shortlist changes.
    }

    @Override
    public void onWarning(String message) {
        out.println("Warning: " + message);
    }

    // ---- helpers ----

    private Listing requireListing(String id) {
        String key = unquote(id == null ? "" : id.trim());
        if (key.isEmpty()) {
            throw new IllegalArgumentException("A listing ID is required, e.g. L014");
        }
        return service.getListing(key.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NoSuchListingException(key.toUpperCase(Locale.ROOT)));
    }

    private static String describe(SearchCriteria c) {
        return c.isEmpty() ? "none (all listings)" : c.describe();
    }

    private static int parseCount(String text, String flag) {
        try {
            int v = Integer.parseInt(text.trim());
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " needs a whole number (was '" + text + "')");
        }
    }

    private static double parseNumber(String text, String flag) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " needs a number (was '" + text + "')");
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /** Splits on whitespace, keeping "double quoted" phrases together. */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char c : text.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Unclosed quote in: " + text);
        }
        if (cur.length() > 0) {
            tokens.add(cur.toString());
        }
        return tokens;
    }
}
