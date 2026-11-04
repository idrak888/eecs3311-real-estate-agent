# Stage 1 -> Stage 2 Changes (working notes for report Section 4)

Format: Original design -> Implemented change -> Reason -> Impact

- **ListingParser interface** -> added `getRejections()` -> F01 must report records rejected
  and why; the Stage 1 interface had no way to return them alongside the parsed list ->
  parsers expose rejection reasons; no change to how callers obtain a parser.
- **RealEstateService.loadListings coordination** -> moved into new `ListingImporter`
  (persistence layer) returning a `LoadSummary` -> keeps the facade thin and makes the import
  path unit-testable without the service -> `RealEstateService.loadListings()` will delegate to
  `ListingImporter`; Factory Method participants unchanged.
- **Listing validation** -> performed in the `Listing` constructor, shared by both parsers via
  `ListingRecordMapper` -> one set of validation rules regardless of file format; an invalid
  Listing cannot be constructed -> no class-diagram change beyond the package-private mapper.
- **ListingRepository** -> added `getDuplicateIds()` -> duplicate IDs in source data are kept
  once and reported in the load summary rather than silently overwritten.
- **Listing.getPricePerSqFt()** -> returns NaN with `hasSquareFeet()` when size is unknown ->
  Stage 1 required "unavailable" rather than zero or infinity.
- **Composite leaf filters** -> added `BathroomFilter` and `NeighborhoodFilter` -> Stage 1
  `SearchCriteria` carried `minBathrooms` and `neighborhoods`, but no leaf filter evaluated
  them, so those criteria would have been silently ignored -> two new leaves in Diagram 1b.
- **AndFilter / OrFilter** -> share an abstract `CompositeFilter` base holding the child list
  -> removes duplicated `add()`/children code; `Filter` gains `describe()` for readable
  criteria summaries (CLI output, zero-match explanation) -> one abstract class added between
  `Filter` and the two composites in Diagram 1b.
- **SearchCriteriaBuilder** -> Stage 1 listed `withMaxPrice`, `withMinBedrooms`, `withType`,
  `withFeature`; implemented builder also has `withMinPrice`, `withMinBathrooms`,
  `withNeighborhood`, `from(SearchCriteria)` and `clear*()` -> every `SearchCriteria` field must
  be settable from GUI controls, CLI flags and LLM output, and the GUI edits one field of the
  session criteria at a time -> more builder methods in Diagram 1b; no structural change.
- **SearchCriteria** -> added `validationErrors()`, `isEmpty()`, `describe()` and `empty()`
  alongside `isValid()`, `toFilter()`, `merge()` -> F03 must say why a search did not run
  (e.g. inverted price range), not just that it was invalid; `toFilter()` refuses invalid
  criteria -> additional methods in Diagram 1b.
- **RankingStrategy** -> added a default `rank(List<Listing>, SearchCriteria)` alongside
  `score()` and `getName()` -> the sort (descending score, ties by ID) is identical for every
  strategy, so it lives once in the interface rather than in the service -> one extra method
  on the interface in Diagram 1b; `RealEstateService` will call `rank()`.
- **BestMatchStrategy scoring** -> average per-criterion satisfaction in [0, 1] with partial
  credit for near misses -> Stage 1 said "best match to criteria" without defining it; partial
  credit keeps the ranking meaningful on relaxed or unfiltered results -> no diagram change.
- **MortgageCalculator** -> added `calculate(price, terms)` returning a `MortgageResult`, and
  `monthlyHousingCost()` -> the service needs all F06 outputs in one call, and F07 needs the
  housing cost figure -> two extra methods in Diagram 1c.
- **MortgageTerms** -> added `defaultsFor(price)` (20% down, 5.0%, 25 years) -> F05 shows a
  payment "at default terms" and F06 says unspecified parameters take configured defaults ->
  one static method.
- **AffordabilityResult** -> added `monthlyHousingCost`, `debtLimited` and `explanation` to
  verdict, GDS, TDS and max price -> F07 must flag "debt alone exceeds thresholds"
  specifically and the GUI/CLI need a readable reason -> three fields in Diagram 1c.
- **Affordability thresholds (decision)** -> GDS 39%, TDS 44%, marginal = within 3 percentage
  points below either; housing cost = payment + 1%/year property tax + $100/month heating ->
  Stage 1 said "standard thresholds" without numbers.
- **ShortlistCommand** -> added `changedState()`; `CommandInvoker.undo()` returns
  `Optional<ShortlistCommand>` instead of void, and gained `peekUndoDescription()` -> a no-op
  (adding a duplicate) must not land on the undo stack, and "nothing to undo" must be reported
  -> one method on the interface, changed return type in Diagram 1c.
- **RemoveFromShortlist.undo()** -> restores the entry at its original position -> undo should
  leave the shortlist exactly as it was, including order.
- **PreferenceStore** -> load methods throw `IOException` for a corrupt file (missing file =
  empty); writes go through a temp file then move -> F09 requires warning the user on
  persistence problems rather than silently losing data.
- **RealEstateService.loadListings** -> returns `LoadSummary` instead of `int` -> F01 must
  report rejected records, not just a count.
- **RealEstateService.getListing** -> returns `Optional<Listing>` -> F05 reports "no such
  listing" for unknown IDs; matches the Stage 1 realization text.
- **RealEstateService additions** -> `refine()`, `restoreSession()`, `setFinancialProfile()`,
  `addToShortlist()` / `removeFromShortlist()` conveniences, `getShortlistedListings()`,
  `canUndo()`; `NoSuchListingException` and `InvalidCriteriaException` -> the frontends need
  these and should not build commands or merge criteria themselves -> extra facade methods.
- **SearchObserver** -> added default methods `onShortlistChanged()` and `onWarning()` ->
  F09 says observers update the shortlist panel, and persistence warnings must reach the UI.
- **LLMClient adapter** -> `AnthropicLLMClient` takes an injectable `Transport` and counts
  input/output tokens; new `EnvConfig` reads `.env`; new `LLMException` -> HTTP can be faked in
  unit tests, and real token totals feed the AI usage log -> helper types beside Diagram 2a.
- **RequirementExtractor** -> added `withVocabulary(neighborhoods, features)`; throws
  `ExtractionException` after the retry -> prompt lists the data set's real neighborhood and
  feature names so extracted criteria actually match listings.
- **MemoryManager** -> added soft preferences (`mergePreferences`, `getRankingCriteria`),
  `setCriteria()` and last-results tracking alongside history, `getCriteria`,
  `mergeCriteria`, `getContext` -> preferences rank without filtering; last results let the
  model resolve "the second one"; `setCriteria` keeps chat in step with the criteria panel.
- **AgentTool framework** -> `ToolRequest` gained typed getters (`getDouble`, `getInt`,
  `getStringList`) accepting numbers or numeric strings; `ToolManager` gained `describeTools()`
  and turns tool exceptions into failed results -> model JSON is loosely typed, and one bad
  tool call must not crash a turn.
- **SearchTool** -> takes separate `criteria` (filter) and `rankingCriteria` (filter + soft
  preferences) -> implements the "maybe in Vaughan" fix found in the Oct 31 live test.
- **MortgageTool** -> also returns the affordability verdict when a profile is available ->
  "can I afford the second one?" is one tool call.
- **ComparisonTool** -> needs `MortgageCalculator` as well as `ListingRepository` -> F08 lists
  monthly payment as a compared attribute; `ComparisonResult` gained `toTable()` for the CLI and
  the explanation prompt.
- **AdvisorAgent.selectTool** -> returns a validated decision (tool + parameters) rather than an
  `AgentTool` -> the model must also say which listing IDs a request refers to; unknown tools
  fall back to search. Mortgage answers are formatted in code from calculator output, so the
  model never writes figures.
- **AgentResponse** -> added mortgage, affordability, notice and aiAvailable fields -> the GUI
  and CLI must show "explanation unavailable" / "AI unavailable" notices (F02, F08, F10).
- **RequirementExtractor** -> added `extractRequest()` returning `ExtractedRequest`
  (requirements + preferences); schema gained `preferredTypes/Neighborhoods/Features` -> live
  test on Oct 31: "maybe in Vaughan or Woodbridge" was extracted as a hard filter and returned
  0 of 60 listings; now those areas rank first and other matches still show.
- **RealEstateService** -> `create(store, llm)` wires agent and tools on the shared repository;
  agent searches update session criteria and notify observers; manual searches update agent
  memory; the extractor gets the data set's neighborhoods and features after each load.
