# Stage 2 diagrams

Updated to match the submitted implementation. Stage 1 originals are unchanged in `diagrams/`.
Classes shaded green were added or substantially changed in Stage 2.

| File | Replaces | What changed |
|------|----------|--------------|
| Diagram1a_ArchitectureSpine.uxf | Diagram1a | Facade gains agent, profile and diagnosis operations; `ListingImporter`, `LoadSummary`, `ListingRecordMapper`, `Main`; observer has four callbacks |
| Diagram1b_Search.uxf | Diagram1b | `BathroomFilter`, `NeighborhoodFilter`, abstract `CompositeFilter`; full builder; `ZeroMatchAnalyzer`; `RankingStrategy.rank()` |
| Diagram1c_CommandsFinancial.uxf | Diagram1c | `changedState()`, undo returns what it undid; calculator thresholds and `calculate()`; richer `AffordabilityResult` |
| Diagram2a_AgentCore.uxf | Diagram2a | Tool decision, `ExtractedRequest` (requirements + preferences), memory preferences/last results, `Transport`, `EnvConfig`, retries |
| Diagram2b_AgentTools.uxf | Diagram2b | Typed `ToolRequest`, tool parameters/data keys, `ComparisonTool` uses `MortgageCalculator`, diagnosis on empty search |
| Diagram3_UserInterface.uxf | (new) | GUI panels and dialogs, `CLIRenderer`, shared `Formats` |
| SD02_NLSearchInterpretation.uxf | SD02 | Model-driven tool selection, requirements vs preferences, clarifying question, observer update |
| SD03_SearchToolExecution.uxf | SD03 | Ranking inside the tool; `ZeroMatchAnalyzer` when nothing matches |

SD04-SD07 and the use-case diagram still match the implementation and are not duplicated.

For the report: open each file in UMLet and use File > Export as... > PNG.
