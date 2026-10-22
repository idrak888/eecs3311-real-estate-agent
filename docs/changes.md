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
