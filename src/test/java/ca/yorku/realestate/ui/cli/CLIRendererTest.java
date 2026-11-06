package ca.yorku.realestate.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.agent.llm.MockLLMClient;
import ca.yorku.realestate.persistence.PreferenceStore;
import ca.yorku.realestate.service.RealEstateService;
import ca.yorku.realestate.ui.Formats;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CLIRendererTest {

    private final MockLLMClient llm = new MockLLMClient();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final RealEstateService service = service();
    private final CLIRenderer cli = new CLIRenderer(service, new BufferedReader(new StringReader("")),
            new PrintStream(buffer, true, StandardCharsets.UTF_8));

    private RealEstateService service() {
        try {
            Path prefs = Files.createTempDirectory("cli");
            prefs.toFile().deleteOnExit();
            RealEstateService s = RealEstateService.create(new PreferenceStore(prefs), llm);
            s.loadListings(new File(getClass().getResource("/data/listings.json").toURI()));
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String run(String line) {
        buffer.reset();
        cli.execute(line);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void searchWithFlagsFiltersAndPrintsTable() {
        String out = run("search --max-price 1M --beds 2 --type condo --type townhouse --sort price-asc");
        assertTrue(out.contains("price <= 1000000 AND bedrooms >= 2"), out);
        assertTrue(out.contains("Ranked by: Price (low to high)"));
        assertTrue(out.contains("(8 listings)"));
        assertTrue(out.contains("ID     Type"));
        assertEquals(8, service.getResults().size());
    }

    @Test
    void searchAloneListsAllWithMoreLine() {
        String out = run("search");
        assertTrue(out.contains("Criteria: none (all listings)"));
        assertTrue(out.contains("... and 50 more (60 total)"));
    }

    @Test
    void quotedMultiWordArea() {
        String out = run("search --area \"Liberty Village\"");
        assertTrue(service.getResults().stream().allMatch(l -> l.getNeighborhood().equals("Liberty Village")));
        assertFalse(out.contains("Error"));
    }

    @Test
    void invertedPriceRangeIsReportedNotRun() {
        String out = run("search --min-price 900k --max-price 500k");
        assertTrue(out.contains("Search not run: Minimum price ($900,000) exceeds maximum price ($500,000)"), out);
    }

    @Test
    void badFlagsAndValuesGiveReadableErrors() {
        assertTrue(run("search --beds three").contains("--beds needs a whole number"));
        assertTrue(run("search --colour blue").contains("Unknown flag --colour"));
        assertTrue(run("search --type castle").contains("Unknown property type"));
        assertTrue(run("search --max-price").contains("--max-price needs a value"));
        assertTrue(run("search house").contains("Flags start with --"));
    }

    @Test
    void zeroMatchesNamesTheBlockingRequirement() {
        String out = run("search --max-price 1.5M --beds 3 --type house --area Vaughan --area Woodbridge --feature garage");
        assertTrue(out.contains("No listings match."), out);
        assertTrue(out.contains("The most restrictive requirement is the area Vaughan or Woodbridge: without it, 3 listings match."), out);
        assertTrue(run("search --max-price 10").contains("No listings match"));
    }

    @Test
    void showPrintsDetailsAndDefaultPayment() {
        String out = run("show l001");
        assertTrue(out.startsWith("L001 - "), out);
        assertTrue(out.contains("Est. payment:"));
        assertTrue(out.contains("20% down, 5.0%, 25 years"));
    }

    @Test
    void showUnknownIdAndUnknownSquareFeet() {
        assertTrue(run("show L999").contains("Error: No listing with ID L999"));
        String unknownSize = service.getResults().stream().filter(l -> !l.hasSquareFeet()).findFirst().orElseThrow().getId();
        assertTrue(run("show " + unknownSize).contains("Square feet:    unavailable"));
    }

    @Test
    void mortgageWithCustomTerms() {
        String out = run("mortgage L001 --down 200k --rate 4.8% --years 30");
        assertTrue(out.contains("$200,000 down, 4.8% over 30 years"), out);
        assertTrue(out.contains("Monthly payment:"));
        assertTrue(run("mortgage L001 --rate 480").contains("Annual rate must be between"));
        assertTrue(run("mortgage L001 --down 99M").contains("No mortgage needed"));
    }

    @Test
    void profileThenAfford() {
        assertTrue(run("afford L001").contains("Set your finances first"));
        assertTrue(run("profile").contains("No financial profile yet"));
        String saved = run("profile 180k 500 170k");
        assertTrue(saved.contains("Max supportable price: about $"), saved);
        assertTrue(run("profile").contains("Income $180,000/year"));
        String out = run("afford L001");
        assertTrue(out.contains("GDS") && out.contains("TDS"), out);
        assertTrue(out.matches("(?s).*(AFFORDABLE|MARGINAL|UNAFFORDABLE).*"));
    }

    @Test
    void shortlistAddRemoveUndo() {
        assertTrue(run("shortlist").contains("Shortlist is empty"));
        assertTrue(run("shortlist add l003").contains("Added L003. Shortlist has 1."));
        assertTrue(run("shortlist add L003").contains("already on the shortlist"));
        assertTrue(run("shortlist").contains("L003"));
        assertTrue(run("shortlist remove L003").contains("Removed L003"));
        assertTrue(run("undo").contains("Undid: Remove L003 from shortlist."));
        assertTrue(run("shortlist add L999").contains("No listing with ID L999"));
        assertEquals(List.of("L003"), service.getShortlist().getEntries());
    }

    @Test
    void undoWithNothingToUndo() {
        assertTrue(run("undo").contains("Nothing to undo."));
    }

    @Test
    void compareWithoutModelShowsTableAndNotice() {
        llm.setAvailable(false);
        String out = run("compare L001 L002");
        assertTrue(out.contains("Attribute"), out);
        assertTrue(out.contains("* = best in row"));
        assertTrue(out.contains("Note: AI summary unavailable"));
        assertTrue(run("compare L001").contains("two to four listings"));
    }

    @Test
    void askShowsAgentAnswerResultsAndTools() {
        llm.setResponse("Decide which tool", "{\"tool\": \"search\"}");
        llm.setResponse("Extract home search criteria", "{\"minBedrooms\": 5}");
        llm.setResponse("Explain these results", "Big family homes.");
        String out = run("ask five bedroom place");
        assertTrue(out.contains("Big family homes."), out);
        assertTrue(out.contains("[tools: search]"));
        assertTrue(out.contains("ID     Type"));
        assertTrue(run("ask").contains("Usage: ask"));
    }

    @Test
    void askWithModelDownSaysSo() {
        llm.setAvailable(false);
        assertTrue(run("ask 3 bed house").contains("isn't available"));
    }

    @Test
    void sortReranksCurrentResults() {
        run("search --type condo");
        String out = run("sort price-desc");
        assertTrue(out.contains("Ranked by: Price (high to low)"));
        assertTrue(run("sort cheapest").contains("Use one of: best, price-asc, price-desc, value"));
    }

    @Test
    void loadReportsRejectionsAndMissingFile() throws Exception {
        File mixed = new File(getClass().getResource("/fixtures/mixed.json").toURI());
        String out = run("load " + mixed.getPath());
        assertTrue(out.contains("Loaded 2 listings from mixed.json (4 rejected)"), out);
        assertTrue(out.contains("rejected record 2 (id=M002)"));
        assertTrue(run("load nowhere.json").contains("not found at expected path"));
        assertTrue(run("load listings.xml").contains("Error:"));
    }

    @Test
    void unknownCommandAndHelpAndQuit() {
        assertTrue(run("dance").contains("Unknown command 'dance'"));
        assertTrue(run("help").contains("compare <id> <id>"));
        assertTrue(run("criteria").contains("Criteria:"));
        assertTrue(run("quit").contains("Goodbye."));
    }

    @Test
    void runLoopReadsUntilQuit() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new CLIRenderer(service, new BufferedReader(new StringReader("search --beds 5\nquit\nsearch\n")),
                new PrintStream(out, true, StandardCharsets.UTF_8)).run();
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("bedrooms >= 5"));
        assertTrue(text.endsWith("Goodbye.\n"), "stops at quit");
    }

    @Test
    void persistenceWarningIsPrinted() throws Exception {
        Path notADir = Files.createTempFile("blocked", ".txt");
        notADir.toFile().deleteOnExit();
        RealEstateService s = RealEstateService.create(new PreferenceStore(notADir), llm);
        s.loadListings(new File(getClass().getResource("/data/listings.json").toURI()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CLIRenderer c = new CLIRenderer(s, new BufferedReader(new StringReader("")), new PrintStream(out, true, StandardCharsets.UTF_8));
        c.execute("shortlist add L001");
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Warning: Shortlist could not be saved"));
    }

    @Test
    void tokenizeKeepsQuotedPhrases() {
        assertEquals(List.of("--area", "Liberty Village", "--beds", "2"),
                CLIRenderer.tokenize("--area \"Liberty Village\"  --beds 2"));
        assertThrows(IllegalArgumentException.class, () -> CLIRenderer.tokenize("--area \"oops"));
    }

    @Test
    void moneyParsing() {
        assertEquals(850_000, Formats.parseMoney("850k"), 1e-9);
        assertEquals(1_200_000, Formats.parseMoney("$1.2M"), 1e-9);
        assertEquals(850_000, Formats.parseMoney("850,000"), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Formats.parseMoney("lots"));
        assertThrows(IllegalArgumentException.class, () -> Formats.parseMoney("-5"));
    }
}
