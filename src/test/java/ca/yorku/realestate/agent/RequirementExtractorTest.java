package ca.yorku.realestate.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.yorku.realestate.agent.llm.LLMException;
import ca.yorku.realestate.agent.llm.MockLLMClient;
import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RequirementExtractorTest {

    private static final String THREE_BED =
            "{\"maxPrice\": 850000, \"minBedrooms\": 3, \"types\": [\"house\"]}";

    @Test
    void extractsCriteriaFromModelJson() {
        MockLLMClient llm = new MockLLMClient();
        llm.setResponse("three bedroom house under 850k", THREE_BED);
        SearchCriteria c = new RequirementExtractor(llm).extract("three bedroom house under 850k");
        assertEquals(850_000.0, c.getMaxPrice());
        assertEquals(Integer.valueOf(3), c.getMinBedrooms());
        assertEquals(Set.of(PropertyType.HOUSE), c.getTypes());
        assertEquals(null, c.getMinPrice(), "unmentioned fields stay unset for merging");
        assertEquals(1, llm.getCallCount());
    }

    @Test
    void allFieldsIncludingNeighborhoodsAndFeatures() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{\"minPrice\": 500000, \"maxPrice\": 900000, \"minBedrooms\": 2,"
                + " \"minBathrooms\": 2, \"types\": [\"condo\", \"Townhouse\"],"
                + " \"neighborhoods\": [\"Downtown\"], \"features\": [\"Parking\", \"gym\"]}");
        SearchCriteria c = new RequirementExtractor(llm).extract("anything");
        assertEquals(500_000.0, c.getMinPrice());
        assertEquals(Integer.valueOf(2), c.getMinBathrooms());
        assertEquals(Set.of(PropertyType.CONDO, PropertyType.TOWNHOUSE), c.getTypes());
        assertEquals(Set.of("Downtown"), c.getNeighborhoods());
        assertEquals(Set.of("parking", "gym"), c.getRequiredFeatures());
    }

    @Test
    void toleratesCodeFencesAndSurroundingProse() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("Sure! Here you go:\n```json\n" + THREE_BED + "\n```");
        assertEquals(Integer.valueOf(3), new RequirementExtractor(llm).extract("x").getMinBedrooms());
    }

    @Test
    void nullsAndEmptyArraysMeanUnset() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{\"maxPrice\": null, \"types\": [], \"features\": null}");
        assertTrue(new RequirementExtractor(llm).extract("x").isEmpty());
    }

    @Test
    void nothingExtractableReturnsEmptyCriteria() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{}");
        assertTrue(new RequirementExtractor(llm).extract("hello there").isEmpty());
    }

    @Test
    void blankInputSkipsTheModel() {
        MockLLMClient llm = new MockLLMClient();
        assertTrue(new RequirementExtractor(llm).extract("   ").isEmpty());
        assertEquals(0, llm.getCallCount());
    }

    @Test
    void invalidJsonRetriesOnceWithStricterPrompt() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("I think you want a house!");
        llm.setResponse(RequirementExtractor.RETRY_MARKER, THREE_BED);
        SearchCriteria c = new RequirementExtractor(llm).extract("family home");
        assertEquals(Integer.valueOf(3), c.getMinBedrooms());
        assertEquals(2, llm.getCallCount());
        assertTrue(llm.getPrompts().get(1).contains("no JSON object"), "retry explains the problem");
    }

    @Test
    void twoBadRepliesAskUserToRephrase() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("not json at all");
        ExtractionException e = assertThrows(ExtractionException.class,
                () -> new RequirementExtractor(llm).extract("something"));
        assertTrue(e.getMessage().contains("rephrase"));
        assertEquals(2, llm.getCallCount());
    }

    @Test
    void wrongValueTypesTriggerRetry() {
        for (String bad : List.of(
                "{\"maxPrice\": \"850k\"}",
                "{\"minBedrooms\": 2.5}",
                "{\"maxPrice\": -1}",
                "{\"types\": \"house\"}",
                "{\"types\": [\"castle\"]}",
                "{\"features\": [1, 2]}",
                "[1, 2, 3]")) {
            MockLLMClient llm = new MockLLMClient();
            llm.setDefaultResponse(bad);
            llm.setResponse(RequirementExtractor.RETRY_MARKER, "{\"minBedrooms\": 1}");
            SearchCriteria c = new RequirementExtractor(llm).extract("q");
            assertEquals(Integer.valueOf(1), c.getMinBedrooms(), bad);
            assertEquals(2, llm.getCallCount(), bad);
        }
    }

    @Test
    void unavailableModelThrowsWithoutCalling() {
        MockLLMClient llm = new MockLLMClient();
        llm.setAvailable(false);
        assertThrows(LLMException.class, () -> new RequirementExtractor(llm).extract("a condo"));
        assertEquals(0, llm.getCallCount());
    }

    @Test
    void promptContainsRulesSchemaAndUserText() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{}");
        new RequirementExtractor(llm).extract("condo near the lake");
        String sent = llm.getPrompts().get(0);
        assertTrue(sent.contains("condo near the lake"));
        assertTrue(sent.contains("850k\" -> 850000"));
        assertTrue(sent.contains("\"minBedrooms\""), "schema is sent");
    }

    @Test
    void vocabularyIsListedInPrompt() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{}");
        new RequirementExtractor(llm)
                .withVocabulary(List.of("Maple", "Downtown"), List.of("garage", "pool"))
                .extract("x");
        String sent = llm.getPrompts().get(0);
        assertTrue(sent.contains("neighborhoods must be from: Downtown, Maple"));
        assertTrue(sent.contains("features must be from: garage, pool"));
    }

    @Test
    void withoutVocabularyNoListsInPrompt() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{}");
        new RequirementExtractor(llm).extract("x");
        assertFalse(llm.getPrompts().get(0).contains("neighborhoods must be from"));
    }

    @Test
    void extractedCriteriaMergeAcrossTurns() {
        MockLLMClient llm = new MockLLMClient();
        llm.setResponse("three bedroom", THREE_BED);
        llm.setResponse("actually up to 900k with a garage", "{\"maxPrice\": 900000, \"features\": [\"garage\"]}");
        RequirementExtractor x = new RequirementExtractor(llm);
        SearchCriteria merged = x.extract("three bedroom house under 850k")
                .merge(x.extract("actually up to 900k with a garage"));
        assertEquals(900_000.0, merged.getMaxPrice());
        assertEquals(Integer.valueOf(3), merged.getMinBedrooms());
        assertEquals(Set.of("garage"), merged.getRequiredFeatures());
    }

    @Test
    void requiresClient() {
        assertThrows(IllegalArgumentException.class, () -> new RequirementExtractor(null));
    }

    // ---- soft preferences ----

    @Test
    void hedgedWishesBecomePreferencesNotRequirements() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{\"maxPrice\": 1500000, \"minBedrooms\": 3, \"types\": [\"house\"],"
                + " \"features\": [\"garage\"], \"preferredNeighborhoods\": [\"Vaughan\", \"Woodbridge\"]}");
        ExtractedRequest r = new RequirementExtractor(llm)
                .extractRequest("3 bedroom house under 1.5M with a garage, maybe in Vaughan or Woodbridge");
        assertTrue(r.required().getNeighborhoods().isEmpty(), "maybe must not filter");
        assertEquals(Set.of("garage"), r.required().getRequiredFeatures());
        assertEquals(Set.of("Vaughan", "Woodbridge"), r.preferred().getNeighborhoods());
    }

    @Test
    void preferredTypesAndFeaturesParse() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{\"preferredTypes\": [\"condo\"], \"preferredFeatures\": [\"Pool\"]}");
        ExtractedRequest r = new RequirementExtractor(llm).extractRequest("ideally a condo with a pool");
        assertTrue(r.required().isEmpty());
        assertEquals(Set.of(PropertyType.CONDO), r.preferred().getTypes());
        assertEquals(Set.of("pool"), r.preferred().getRequiredFeatures());
        assertFalse(r.isEmpty());
    }

    @Test
    void invalidPreferredTypeTriggersRetry() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{\"preferredTypes\": [\"castle\"]}");
        llm.setResponse(RequirementExtractor.RETRY_MARKER, "{\"preferredTypes\": [\"house\"]}");
        assertEquals(Set.of(PropertyType.HOUSE),
                new RequirementExtractor(llm).extractRequest("q").preferred().getTypes());
        assertEquals(2, llm.getCallCount());
    }

    @Test
    void promptExplainsHedgedWishRule() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{}");
        new RequirementExtractor(llm).extract("x");
        String sent = llm.getPrompts().get(0);
        assertTrue(sent.contains("\"maybe\""));
        assertTrue(sent.contains("preferredNeighborhoods"));
    }

    @Test
    void extractReturnsOnlyRequirements() {
        MockLLMClient llm = new MockLLMClient();
        llm.setDefaultResponse("{\"minBedrooms\": 2, \"preferredFeatures\": [\"gym\"]}");
        SearchCriteria c = new RequirementExtractor(llm).extract("2 bed, gym would be nice");
        assertEquals(Integer.valueOf(2), c.getMinBedrooms());
        assertTrue(c.getRequiredFeatures().isEmpty());
    }
}
