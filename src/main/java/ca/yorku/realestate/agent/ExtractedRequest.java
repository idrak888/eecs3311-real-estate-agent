package ca.yorku.realestate.agent;

import ca.yorku.realestate.domain.SearchCriteria;

/**
 * What the extractor found in one message: hard requirements, which filter listings, and soft
 * preferences ("maybe in Vaughan", "ideally with a pool"), which only affect ranking.
 */
public record ExtractedRequest(SearchCriteria required, SearchCriteria preferred) {

    public ExtractedRequest {
        required = required == null ? SearchCriteria.empty() : required;
        preferred = preferred == null ? SearchCriteria.empty() : preferred;
    }

    public boolean isEmpty() {
        return required.isEmpty() && preferred.isEmpty();
    }
}
