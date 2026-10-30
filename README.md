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
  agent/         RequirementExtractor (natural language -> SearchCriteria)
    llm/         LLMClient (Adapter): AnthropicLLMClient, MockLLMClient
  config/        EnvConfig (.env loading)
  persistence/   ListingRepository, ListingImporter, LoadSummary, PreferenceStore
    parser/      ListingParser (Factory Method): JSON and CSV parsers
src/main/resources/data/
  listings.json  60 sample listings (same data also as listings.csv)
```

GUI and CLI launch instructions will be added as those layers are implemented.
