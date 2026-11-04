package ca.yorku.realestate.agent;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversational state for the agent (F02, F10).
 *
 * <p>Holds three things:
 * <ul>
 *   <li><b>Criteria</b> that accumulate across turns. New criteria merge into the session
 *       criteria (later values override, unmentioned fields persist), so a budget given in the
 *       first message still constrains the tenth search.</li>
 *   <li><b>Preferences</b>: soft wishes ("ideally near transit") that rank results without
 *       filtering them out. They merge the same way.</li>
 *   <li><b>History</b> of turns and the most recent results, so follow-ups like "the second one"
 *       can be resolved and prompts can include relevant context.</li>
 * </ul>
 * Only the last {@value #MAX_TURNS} turns are kept, so prompts stay bounded.
 */
public class MemoryManager {

    /** Oldest turns are dropped beyond this many. */
    public static final int MAX_TURNS = 20;
    /** Longest agent reply quoted in prompt context; longer replies are truncated. */
    static final int MAX_QUOTED_REPLY = 300;

    private final List<ConversationTurn> history = new ArrayList<>();
    private SearchCriteria criteria = SearchCriteria.empty();
    private SearchCriteria preferences = SearchCriteria.empty();
    private List<Listing> lastResults = List.of();

    public void addTurn(ConversationTurn turn) {
        if (turn == null) {
            throw new IllegalArgumentException("turn is required");
        }
        history.add(turn);
        while (history.size() > MAX_TURNS) {
            history.remove(0);
        }
    }

    public List<ConversationTurn> getHistory() {
        return List.copyOf(history);
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    /** Merges newer criteria into the session criteria; returns the merged result. */
    public SearchCriteria mergeCriteria(SearchCriteria newer) {
        criteria = criteria.merge(newer);
        return criteria;
    }

    /** Replaces the session criteria outright, e.g. when the user edits the criteria panel. */
    public void setCriteria(SearchCriteria replacement) {
        criteria = replacement == null ? SearchCriteria.empty() : replacement;
    }

    public SearchCriteria getPreferences() {
        return preferences;
    }

    public SearchCriteria mergePreferences(SearchCriteria newer) {
        preferences = preferences.merge(newer);
        return preferences;
    }

    /** Criteria and preferences combined, for best-match ranking. Hard criteria win on conflict. */
    public SearchCriteria getRankingCriteria() {
        return preferences.merge(criteria);
    }

    public List<Listing> getLastResults() {
        return lastResults;
    }

    public void setLastResults(List<Listing> results) {
        lastResults = results == null ? List.of() : List.copyOf(results);
    }

    /**
     * Context for a prompt: the active criteria and preferences, the last results with their
     * positions (so "the second one" can be resolved), and the last {@code maxTurns} turns.
     */
    public String getContext(int maxTurns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Active search criteria: ")
          .append(criteria.isEmpty() ? "none" : criteria.describe()).append('\n');
        if (!preferences.isEmpty()) {
            sb.append("Soft preferences (rank, do not filter): ").append(preferences.describe()).append('\n');
        }
        if (!lastResults.isEmpty()) {
            sb.append("Most recent results, in displayed order:\n");
            int shown = Math.min(lastResults.size(), 10);
            for (int i = 0; i < shown; i++) {
                sb.append("  ").append(i + 1).append(". ").append(lastResults.get(i)).append('\n');
            }
            if (lastResults.size() > shown) {
                sb.append("  (").append(lastResults.size() - shown).append(" more not shown)\n");
            }
        }
        int n = Math.max(0, Math.min(maxTurns, history.size()));
        if (n > 0) {
            sb.append("Recent conversation:\n");
            for (ConversationTurn t : history.subList(history.size() - n, history.size())) {
                sb.append("  User: ").append(t.getUserMessage()).append('\n');
                sb.append("  Advisor: ").append(truncate(t.getAgentResponse())).append('\n');
            }
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        String oneLine = s.replace('\n', ' ').trim();
        return oneLine.length() <= MAX_QUOTED_REPLY ? oneLine
                : oneLine.substring(0, MAX_QUOTED_REPLY) + "...";
    }

    /** Forgets everything: criteria, preferences, results and history. */
    public void clear() {
        history.clear();
        criteria = SearchCriteria.empty();
        preferences = SearchCriteria.empty();
        lastResults = List.of();
    }
}
