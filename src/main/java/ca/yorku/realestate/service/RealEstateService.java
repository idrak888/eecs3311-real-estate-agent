package ca.yorku.realestate.service;

import ca.yorku.realestate.agent.AdvisorAgent;
import ca.yorku.realestate.agent.AgentResponse;
import ca.yorku.realestate.agent.MemoryManager;
import ca.yorku.realestate.agent.RequirementExtractor;
import ca.yorku.realestate.agent.llm.LLMClient;
import ca.yorku.realestate.agent.tool.ComparisonTool;
import ca.yorku.realestate.agent.tool.MortgageTool;
import ca.yorku.realestate.agent.tool.SearchTool;
import ca.yorku.realestate.agent.tool.ToolManager;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.ZeroMatchAnalyzer;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageCalculator;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.domain.ranking.BestMatchStrategy;
import ca.yorku.realestate.domain.ranking.RankingStrategy;
import ca.yorku.realestate.domain.shortlist.AddToShortlist;
import ca.yorku.realestate.domain.shortlist.CommandInvoker;
import ca.yorku.realestate.domain.shortlist.RemoveFromShortlist;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import ca.yorku.realestate.domain.shortlist.ShortlistCommand;
import ca.yorku.realestate.persistence.ListingImporter;
import ca.yorku.realestate.persistence.ListingRepository;
import ca.yorku.realestate.persistence.LoadSummary;
import ca.yorku.realestate.persistence.PreferenceStore;
import ca.yorku.realestate.persistence.parser.ListingParserFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Facade over the whole system. The GUI and CLI talk only to this class, so neither contains
 * business logic and both behave identically.
 *
 * <p>The service holds session state: the active criteria and results, the ranking strategy,
 * the shortlist with its undo history, and the financial profile. Every change is pushed to
 * registered {@link SearchObserver}s.
 *
 * <p>Agent operations ({@link #askAgent}, {@link #compare}) go to the {@link AdvisorAgent}.
 * Searches made by the agent update the same session criteria and results as manual searches,
 * so the criteria panel shows what the agent understood, and edits made in the panel carry
 * into the next chat message.
 */
public class RealEstateService {

    private final ListingRepository repository;
    private final ListingImporter importer;
    private final PreferenceStore store;
    private final MortgageCalculator calculator;
    private final CommandInvoker invoker = new CommandInvoker();
    private final List<SearchObserver> observers = new CopyOnWriteArrayList<>();

    private Shortlist shortlist = new Shortlist();
    private SearchCriteria criteria = SearchCriteria.empty();
    private List<Listing> results = List.of();
    private RankingStrategy strategy = new BestMatchStrategy();
    private FinancialProfile profile;
    private AdvisorAgent agent;
    private RequirementExtractor extractor;

    public RealEstateService(ListingRepository repository, ListingParserFactory parserFactory,
                             PreferenceStore store, MortgageCalculator calculator) {
        this.repository = repository;
        this.importer = new ListingImporter(parserFactory, repository);
        this.store = store;
        this.calculator = calculator;
    }

    /** Standard wiring without an agent; {@link #askAgent} reports the assistant unavailable. */
    public static RealEstateService create(PreferenceStore store) {
        return new RealEstateService(new ListingRepository(), new ListingParserFactory(), store,
                new MortgageCalculator());
    }

    /** Standard wiring with the agent and its tools on the same repository and calculator. */
    public static RealEstateService create(PreferenceStore store, LLMClient llm) {
        ListingRepository repository = new ListingRepository();
        MortgageCalculator calculator = new MortgageCalculator();
        RealEstateService service = new RealEstateService(repository, new ListingParserFactory(), store, calculator);

        ToolManager tools = new ToolManager();
        tools.register(new SearchTool(repository));
        tools.register(new MortgageTool(repository, calculator));
        tools.register(new ComparisonTool(repository, calculator));
        RequirementExtractor extractor = new RequirementExtractor(llm);
        service.attachAgent(new AdvisorAgent(llm, extractor, tools, new MemoryManager()), extractor);
        return service;
    }

    /** Connects an agent; the extractor's vocabulary is refreshed whenever listings load. */
    public void attachAgent(AdvisorAgent advisor, RequirementExtractor requirementExtractor) {
        this.agent = advisor;
        this.extractor = requirementExtractor;
        if (agent != null) {
            agent.setRankingStrategy(strategy);
            agent.setFinancialProfile(profile);
        }
        refreshVocabulary();
    }

    public boolean hasAgent() {
        return agent != null;
    }

    private void refreshVocabulary() {
        if (extractor == null) {
            return;
        }
        Set<String> neighborhoods = new TreeSet<>();
        Set<String> features = new TreeSet<>();
        for (Listing l : repository.findAll()) {
            neighborhoods.add(l.getNeighborhood());
            features.addAll(l.getFeatures());
        }
        extractor.withVocabulary(neighborhoods, features);
    }

    // ---- observers ----

    public void addObserver(SearchObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(SearchObserver observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        for (SearchObserver o : observers) {
            o.onCriteriaChanged(criteria);
            o.onResultsChanged(results);
        }
    }

    private void notifyShortlist() {
        for (SearchObserver o : observers) {
            o.onShortlistChanged(shortlist);
        }
    }

    private void warn(String message) {
        for (SearchObserver o : observers) {
            o.onWarning(message);
        }
    }

    // ---- F01 ----

    /**
     * Loads listings, replacing the current set, and shows them all ranked by the active strategy.
     *
     * @throws IOException if the file is missing, unreadable or malformed as a whole
     */
    public LoadSummary loadListings(File file) throws IOException {
        LoadSummary summary = importer.importFile(file);
        refreshVocabulary();
        criteria = SearchCriteria.empty();
        results = strategy.rank(repository.findAll(), criteria);
        notifyObservers();
        return summary;
    }

    public int getListingCount() {
        return repository.count();
    }

    /** Neighborhoods present in the loaded data, sorted, for the criteria panel. */
    public List<String> availableNeighborhoods() {
        Set<String> names = new TreeSet<>();
        repository.findAll().forEach(l -> names.add(l.getNeighborhood()));
        return new ArrayList<>(names);
    }

    /** Features present in the loaded data, sorted, for the criteria panel. */
    public List<String> availableFeatures() {
        Set<String> names = new TreeSet<>();
        repository.findAll().forEach(l -> names.addAll(l.getFeatures()));
        return new ArrayList<>(names);
    }

    /**
     * Restores the saved shortlist and financial profile. Problems are returned (and sent to
     * observers) as warnings; a corrupt file leaves that item empty rather than failing startup.
     */
    public List<String> restoreSession() {
        List<String> warnings = new ArrayList<>();
        try {
            shortlist = store.loadShortlist();
            invoker.clear();
        } catch (IOException e) {
            warnings.add("Saved shortlist could not be restored: " + e.getMessage());
        }
        try {
            profile = store.loadProfile().orElse(null);
            if (agent != null) {
                agent.setFinancialProfile(profile);
            }
        } catch (IOException e) {
            warnings.add("Saved financial profile could not be restored: " + e.getMessage());
        }
        warnings.forEach(this::warn);
        notifyShortlist();
        return warnings;
    }

    // ---- F02 / F03 / F04 ----

    /**
     * Replaces the session criteria, searches, ranks, and notifies observers.
     *
     * @throws InvalidCriteriaException if the criteria are invalid; nothing changes
     */
    public List<Listing> search(SearchCriteria newCriteria) {
        SearchCriteria c = newCriteria == null ? SearchCriteria.empty() : newCriteria;
        if (!c.isValid()) {
            throw new InvalidCriteriaException(c.validationErrors());
        }
        criteria = c;
        results = strategy.rank(repository.findMatching(c.toFilter()), c);
        if (agent != null) {
            agent.getMemory().setCriteria(c);
            agent.getMemory().setLastResults(results);
        }
        notifyObservers();
        return results;
    }

    /** Merges new criteria into the session criteria (later values win) and searches. */
    public List<Listing> refine(SearchCriteria additional) {
        return search(criteria.merge(additional));
    }

    public SearchCriteria getCriteria() {
        return criteria;
    }

    /**
     * When the current search found nothing, names the most restrictive requirement and how many
     * listings match without it. Empty when there are results or no criteria.
     */
    public Optional<ZeroMatchAnalyzer.Diagnosis> diagnoseNoResults() {
        if (!results.isEmpty() || criteria.isEmpty() || !criteria.isValid()) {
            return Optional.empty();
        }
        return Optional.of(ZeroMatchAnalyzer.analyze(criteria, repository.findAll()));
    }

    public List<Listing> getResults() {
        return results;
    }

    /** Changes the ranking and re-ranks the current results without searching again. */
    public void setRankingStrategy(RankingStrategy newStrategy) {
        if (newStrategy == null) {
            throw new IllegalArgumentException("ranking strategy is required");
        }
        strategy = newStrategy;
        if (agent != null) {
            agent.setRankingStrategy(newStrategy);
        }
        results = strategy.rank(results, criteria);
        notifyObservers();
    }

    public RankingStrategy getRankingStrategy() {
        return strategy;
    }

    // ---- F02 / F08 / F10: agent ----

    /**
     * Sends a chat message to the agent. When the agent searched, the session criteria and
     * results are updated and observers notified, exactly as for a manual search.
     */
    public AgentResponse askAgent(String message) {
        if (agent == null) {
            return AgentResponse.unavailable("The AI assistant is not set up. You can still search "
                    + "with the criteria panel or the CLI search command.");
        }
        AgentResponse response = agent.handleQuery(message);
        if (response.getToolsUsed().contains(SearchTool.NAME) && response.getCriteria() != null) {
            criteria = response.getCriteria();
            results = response.getListings();
            notifyObservers();
        }
        return response;
    }

    /**
     * Compares two to four listings. The matrix is always built; the AI summary is added when
     * the model is available.
     */
    public AgentResponse compare(List<String> listingIds) {
        if (agent == null) {
            return AgentResponse.unavailable("Comparison needs the agent layer, which is not set up.");
        }
        return agent.compareProperties(listingIds);
    }

    // ---- F05 ----

    /** @return the listing, or empty for an unknown ID */
    public Optional<Listing> getListing(String id) {
        return repository.findById(id);
    }

    private Listing requireListing(String id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchListingException(id));
    }

    // ---- F06 ----

    /**
     * @param terms financing terms, or {@code null} for the defaults
     * @throws NoSuchListingException for an unknown ID
     */
    public MortgageResult calculateMortgage(String listingId, MortgageTerms terms) {
        Listing listing = requireListing(listingId);
        MortgageTerms t = terms != null ? terms : MortgageTerms.defaultsFor(listing.getPrice());
        return calculator.calculate(listing.getPrice(), t);
    }

    // ---- F07 ----

    /** Stores the profile for the session and saves it; a save failure is a warning only. */
    public void setFinancialProfile(FinancialProfile newProfile) {
        if (newProfile == null) {
            throw new IllegalArgumentException("financial profile is required");
        }
        profile = newProfile;
        if (agent != null) {
            agent.setFinancialProfile(newProfile);
        }
        try {
            store.saveProfile(newProfile);
        } catch (IOException e) {
            warn("Financial profile could not be saved; it will not be remembered next session.");
        }
    }

    public Optional<FinancialProfile> getFinancialProfile() {
        return Optional.ofNullable(profile);
    }

    /**
     * @param overrideProfile a profile to use for this assessment only, or {@code null} to use
     *     the session profile
     * @throws IllegalStateException if no profile was given and none is set; the UI should
     *     prompt for one rather than assume defaults
     * @throws NoSuchListingException for an unknown ID
     */
    public AffordabilityResult assessAffordability(String listingId, FinancialProfile overrideProfile) {
        Listing listing = requireListing(listingId);
        FinancialProfile p = overrideProfile != null ? overrideProfile : profile;
        if (p == null) {
            throw new IllegalStateException("Enter your income, monthly debt and down payment first.");
        }
        return calculator.assess(listing, p);
    }

    public double maxSupportablePrice() {
        if (profile == null) {
            throw new IllegalStateException("Enter your income, monthly debt and down payment first.");
        }
        return calculator.maxSupportablePrice(profile);
    }

    // ---- F09 ----

    public Shortlist getShortlist() {
        return shortlist;
    }

    /**
     * Runs a shortlist command through the invoker, saves, and notifies observers. If saving
     * fails the in-memory shortlist is still correct and observers receive a warning.
     */
    public void executeCommand(ShortlistCommand command) {
        invoker.execute(command);
        if (command.changedState()) {
            persistShortlist();
        }
        notifyShortlist();
    }

    /**
     * @return false if the listing was already shortlisted (a no-op, not a duplicate)
     * @throws NoSuchListingException for an unknown ID
     */
    public boolean addToShortlist(String listingId) {
        requireListing(listingId);
        AddToShortlist command = new AddToShortlist(shortlist, listingId);
        executeCommand(command);
        return command.changedState();
    }

    /** @return false if the listing was not on the shortlist */
    public boolean removeFromShortlist(String listingId) {
        RemoveFromShortlist command = new RemoveFromShortlist(shortlist, listingId);
        executeCommand(command);
        return command.changedState();
    }

    /** Shortlisted listings, in shortlist order. IDs no longer in the data set are skipped. */
    public List<Listing> getShortlistedListings() {
        List<Listing> out = new ArrayList<>();
        for (String id : shortlist.getEntries()) {
            repository.findById(id).ifPresent(out::add);
        }
        return out;
    }

    /**
     * Undoes the last shortlist action.
     *
     * @return a description of what was undone, or empty if there was nothing to undo
     */
    public Optional<String> undo() {
        Optional<ShortlistCommand> undone = invoker.undo();
        if (undone.isPresent()) {
            persistShortlist();
            notifyShortlist();
        }
        return undone.map(ShortlistCommand::getDescription);
    }

    public boolean canUndo() {
        return invoker.canUndo();
    }

    private void persistShortlist() {
        try {
            store.saveShortlist(shortlist);
        } catch (IOException e) {
            warn("Shortlist could not be saved; changes will not persist after this session.");
        }
    }
}
