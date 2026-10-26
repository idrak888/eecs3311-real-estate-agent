# AI Tools and Collaboration Log (Stage 2)

Running log for report Section 5. Add a row per session.

| Date | Tool / platform | Model | Task (feature / classes) | Tokens | Cost (USD) |
|------|-----------------|-------|--------------------------|--------|------------|
| 2026-10-21 | Claude (claude.ai) | Claude Opus 5.5 | Stage 2 implementation plan; scaffolded Maven project, Listing/PropertyType (F05), JSON/CSV parsers + ListingParserFactory (F01), ListingRepository + ListingImporter (F01), sample dataset, JUnit tests | | |
| 2026-10-22 | Claude (claude.ai) | Claude Opus 5.5 | Tooling: diagnosed stalled Homebrew Maven install (Intel Mac builds from source); switched to Eclipse's embedded Maven | | |
| 2026-10-26 | Claude (claude.ai) | Claude Opus 5.5 | F03: Composite filters (Filter, And/OrFilter, 6 leaves), ListingRepository.findMatching; F02/F03: SearchCriteria + SearchCriteriaBuilder (Builder), merge, validation; tests | | |

## Representative interactions

- 2026-10-21: Asked for a commit-by-commit Stage 2 plan mapped to F01–F10, then for the first
  three commits. Reviewed generated code, ran the tests, and committed in three parts.
- 2026-10-26: Before generating filters, checked Diagram 1b against SearchCriteria and found
  minBathrooms and neighborhoods had no leaf filter; added BathroomFilter and
  NeighborhoodFilter rather than leave those criteria silently ignored.
