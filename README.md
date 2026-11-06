# Real Estate Advisory Agent (EECS3311 Course Project)

A conversational real estate advisor with a Swing GUI and a CLI. The user describes what they
want in plain language; the system extracts structured criteria with an LLM, then runs
deterministic search, ranking, and mortgage/affordability calculations against a local
listing dataset. The LLM interprets and explains; ordinary code computes.

Stage 1 design report: `3311 Project - Stage 1.pdf`. UMLet diagrams: `diagrams/`.

## Prerequisites

- Java 17 or later
- Maven 3.8 or later

## Build and test

```bash
mvn test
```

## Local configuration (LLM API key)

The API key is never committed. Copy the example file and put your own key in the copy:

```bash
cp .env.example .env
# then edit .env and replace YOUR_LOCAL_API_KEY_HERE
```

`.env` is listed in `.gitignore`. Unit tests never call the real API (they use
`MockLLMClient`), so `mvn test` works without a key. The shortlist and financial profile are
saved under `data/user/`, which is also git-ignored.

## Project layout

```
src/main/java/ca/yorku/realestate/
  domain/        Listing, PropertyType, SearchCriteria, SearchCriteriaBuilder
    filter/      Filter (Composite): And/Or + price, bed, bath, type, neighborhood, feature
    ranking/     RankingStrategy (Strategy): price asc/desc, value per sq ft, best match
    finance/     MortgageCalculator, MortgageTerms/Result, FinancialProfile, AffordabilityResult
    shortlist/   Shortlist, ShortlistCommand (Command), CommandInvoker (undo)
  service/       RealEstateService (Facade), SearchObserver (Observer)
  agent/         AdvisorAgent, RequirementExtractor, MemoryManager, AgentResponse
    tool/        AgentTool, ToolManager, SearchTool, MortgageTool, ComparisonTool
    llm/         LLMClient (Adapter): AnthropicLLMClient, MockLLMClient
  config/        EnvConfig (.env loading)
  ui/            Formats (shared formatting)
    cli/         CLIRenderer
    gui/         MainWindow, CriteriaPanel, ChatPanel, ShortlistPanel, detail/comparison dialogs
  app/           Main (entry point), LlmConnectionCheck (one-off live API check)
  persistence/   ListingRepository, ListingImporter, LoadSummary, PreferenceStore
    parser/      ListingParser (Factory Method): JSON and CSV parsers
src/main/resources/data/
  listings.json  60 sample listings (same data also as listings.csv)
```

## Running the application

Both frontends use the same `RealEstateService` facade, so they behave identically.

**From Eclipse:** open `src/main/java/ca/yorku/realestate/app/Main.java` and choose
Run As > Java Application (GUI). For the CLI, add `--cli` under Run Configurations > Arguments.
Run from the project root so `.env` is found.

**From a terminal:**

```bash
mvn compile exec:java                      # GUI
mvn compile exec:java -Dexec.args="--cli"  # CLI
```

Options: `--cli`, and `--data path/to/listings.csv` to load a different file.

Without an API key, everything works except the chat/`ask` command, which reports that the
assistant is unavailable.

### GUI

- **Left:** structured search criteria and ranking. The panel also fills in automatically when
  the advisor interprets a chat message.
- **Centre:** results (double-click for details, mortgage and affordability) and advisor chat.
- **Right:** shortlist. Select 2-4 and press Compare. Edit > Undo reverses shortlist changes.
- **Profile > Financial profile...** sets income, monthly debt and down payment for affordability.

### CLI

```
search --max-price 900k --beds 3 --type house --area Vaughan --feature garage --sort value
ask 3 bedroom house under 1.5M, maybe in Vaughan or Woodbridge
show L020
mortgage L020 --down 150k --rate 4.8 --years 25
profile 150k 600 160k
afford L020
shortlist add L020
compare L020 L009 L036
undo
help
```
