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

## Standalone CLI (`java -jar Agent.jar`)

The agent runs directly from a terminal as a single runnable jar. A prebuilt `Agent.jar` is in
the repository root; to rebuild it from source run `mvn package` (or `mvn package -DskipTests`),
which writes `target/Agent.jar`.

**Working example** (from the repository root, with `LLM_API_KEY` set in `.env` or the
environment):

```bash
java -jar Agent.jar -LLMAPI -input task.txt
```

This runs the agent on the natural-language task in [`task.txt`](task.txt): a 3 bedroom house
search with a hedged area preference, then "compare the top two", then a mortgage question about
the first result. Each block of text separated by a blank line is one request, and later requests
can refer to earlier answers. The agent prints each request, its answer, the tools it used and
the tokens consumed. More tasks are in [`tasks/`](tasks/) (`tasks/impossible-request.txt` shows
the no-match error case).

### Options

Options take one or two dashes and are case-insensitive (`-LLMAPI`, `--llmapi`).

| Option | Meaning |
|---|---|
| *(none)* | start the GUI |
| `-input FILE` | run the agent on the natural-language task in FILE, print the answers, exit |
| `-LLMAPI [anthropic]` | require the LLM API; exits with code 3 if no API key is configured |
| `-model NAME` | LLM model (default: `LLM_MODEL` from `.env`, else `claude-sonnet-5-5`) |
| `-output FILE` | also save the transcript to FILE |
| `-cli` | interactive CLI reading commands from standard input (keyboard, pipe or redirect) |
| `-script FILE`, `-f FILE` | run CLI commands (see below) from FILE and exit; each command is echoed |
| `-c "COMMAND"` | run one CLI command and exit; repeatable |
| `-data FILE` | load listings from a `.json` or `.csv` file instead of the bundled data |
| `-help` | list the options |

Exit codes: 0 success, 1 the AI could not answer a task request (for example a network error),
2 bad arguments or an unreadable file, 3 `-LLMAPI` with no API key.

More examples:

```bash
java -jar Agent.jar -LLMAPI -input tasks/condo-first-home.txt -output run.txt
java -jar Agent.jar -script scripts/demo-commands.txt          # CLI commands from a file
java -jar Agent.jar -cli < scripts/demo-commands.txt            # same, through standard input
java -jar Agent.jar -c "show L020" -c "mortgage L020 --down 150k --rate 4.8 --years 25"
java -jar Agent.jar -data scripts/sample-with-errors.csv -c "search"   # import with rejected rows
```

The same options work with Maven (`mvn -q compile exec:java -Dexec.args="-input task.txt"`)
and in Eclipse (Run Configurations > Arguments). Programs can also call the agent directly through
the `RealEstateService` facade (`askAgent`, `search`, `compare`, `calculateMortgage`), which is
what the GUI and CLI use.

Without an API key, everything works except the chat/`ask` command, which reports that the
assistant is unavailable.

## GUI layout

- **Left:** structured search criteria and ranking. The panel also fills in automatically when
  the advisor interprets a chat message.
- **Centre:** results (double-click for details, mortgage and affordability) and advisor chat.
- **Right:** shortlist. Select 2-4 and press Compare. Edit > Undo reverses shortlist changes.
- **Profile > Financial profile...** sets income, monthly debt and down payment for affordability.

## CLI commands

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
