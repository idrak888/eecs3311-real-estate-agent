package ca.yorku.realestate.agent;

import ca.yorku.realestate.agent.tool.ComparisonResult;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.MortgageResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything the agent produced for one request, for the GUI chat panel and the CLI.
 *
 * <p>{@link #getMessage()} is what to show the user. Listings, the comparison matrix and
 * financial results come from deterministic tools; only the explanation text is generated.
 * {@link #getNotice()} carries a secondary note such as "explanation unavailable".
 */
public final class AgentResponse {

    private final String message;
    private final List<Listing> listings;
    private final SearchCriteria criteria;
    private final ComparisonResult comparison;
    private final MortgageResult mortgage;
    private final AffordabilityResult affordability;
    private final List<String> toolsUsed;
    private final String notice;
    private final boolean aiAvailable;

    private AgentResponse(Builder b) {
        this.message = b.message == null ? "" : b.message;
        this.listings = List.copyOf(b.listings);
        this.criteria = b.criteria;
        this.comparison = b.comparison;
        this.mortgage = b.mortgage;
        this.affordability = b.affordability;
        this.toolsUsed = List.copyOf(b.toolsUsed);
        this.notice = b.notice;
        this.aiAvailable = b.aiAvailable;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Response used when the language model is not configured or unreachable. */
    public static AgentResponse unavailable(String message) {
        return builder().message(message).aiAvailable(false).build();
    }

    public String getMessage() {
        return message;
    }

    public List<Listing> getListings() {
        return listings;
    }

    /** Session criteria after this request, or null if the request did not involve search. */
    public SearchCriteria getCriteria() {
        return criteria;
    }

    public Optional<ComparisonResult> getComparison() {
        return Optional.ofNullable(comparison);
    }

    public Optional<MortgageResult> getMortgage() {
        return Optional.ofNullable(mortgage);
    }

    public Optional<AffordabilityResult> getAffordability() {
        return Optional.ofNullable(affordability);
    }

    /** Names of tools invoked, in order. Empty when no tool was needed. */
    public List<String> getToolsUsed() {
        return toolsUsed;
    }

    public Optional<String> getNotice() {
        return Optional.ofNullable(notice);
    }

    public boolean isAiAvailable() {
        return aiAvailable;
    }

    /** Builder for responses. */
    public static final class Builder {
        private String message;
        private final List<Listing> listings = new ArrayList<>();
        private SearchCriteria criteria;
        private ComparisonResult comparison;
        private MortgageResult mortgage;
        private AffordabilityResult affordability;
        private final List<String> toolsUsed = new ArrayList<>();
        private String notice;
        private boolean aiAvailable = true;

        public Builder message(String m) {
            message = m;
            return this;
        }

        public Builder listings(List<Listing> l) {
            listings.clear();
            if (l != null) {
                listings.addAll(l);
            }
            return this;
        }

        public Builder criteria(SearchCriteria c) {
            criteria = c;
            return this;
        }

        public Builder comparison(ComparisonResult c) {
            comparison = c;
            return this;
        }

        public Builder mortgage(MortgageResult m) {
            mortgage = m;
            return this;
        }

        public Builder affordability(AffordabilityResult a) {
            affordability = a;
            return this;
        }

        public Builder tool(String name) {
            toolsUsed.add(name);
            return this;
        }

        public Builder notice(String n) {
            notice = n;
            return this;
        }

        public Builder aiAvailable(boolean a) {
            aiAvailable = a;
            return this;
        }

        public AgentResponse build() {
            return new AgentResponse(this);
        }
    }
}
