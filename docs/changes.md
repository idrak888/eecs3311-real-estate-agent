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
