package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.agent.AgentResponse;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.persistence.LoadSummary;
import ca.yorku.realestate.service.InvalidCriteriaException;
import ca.yorku.realestate.service.RealEstateService;
import ca.yorku.realestate.service.SearchObserver;
import ca.yorku.realestate.domain.shortlist.Shortlist;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Main Swing window. Observes {@link RealEstateService}: every search, re-rank and agent search
 * arrives through {@link #onResultsChanged} and {@link #onCriteriaChanged}, so the table and the
 * criteria panel always show the service's state, including searches the agent runs from the
 * chat panel. The window holds no business logic.
 */
public final class MainWindow extends JFrame implements SearchObserver {

    private static final long serialVersionUID = 1L;

    private final transient RealEstateService service;
    private final CriteriaPanel criteriaPanel = new CriteriaPanel();
    private final ResultsTableModel resultsModel = new ResultsTableModel();
    private final JTable resultsTable = new JTable(resultsModel);
    private final JLabel resultCount = new JLabel(" ");
    private final ChatPanel chatPanel;
    private final ShortlistPanel shortlistPanel = new ShortlistPanel();
    private final JMenuItem undoItem = new JMenuItem("Undo");
    private final JLabel status = new JLabel(" ");
    private String loadStatus = "";
    private final boolean aiConfigured;
    private boolean aiFailing;

    public MainWindow(RealEstateService service, LoadSummary summary, boolean aiConfigured) {
        super("Real Estate Advisor");
        this.service = service;
        this.aiConfigured = aiConfigured;
        this.chatPanel = new ChatPanel(service::askAgent, this::onAgentResponse, aiConfigured);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 640));
        setPreferredSize(new Dimension(1280, 800));

        setJMenuBar(buildMenu());
        JScrollPane criteriaScroll = new JScrollPane(criteriaPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        criteriaScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, java.awt.Color.LIGHT_GRAY));
        criteriaScroll.setPreferredSize(new Dimension(criteriaPanel.getPreferredSize().width + 20, 600));
        add(criteriaScroll, BorderLayout.WEST);
        javax.swing.JSplitPane split = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT,
                buildCenter(), chatPanel);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        add(shortlistPanel, BorderLayout.EAST);
        add(buildStatusBar(), BorderLayout.SOUTH);

        criteriaPanel.setVocabulary(service.availableNeighborhoods(), service.availableFeatures());
        criteriaPanel.onSearch(this::runSearch);
        criteriaPanel.onClear(() -> {
            criteriaPanel.clear();
            runSearch();
        });
        criteriaPanel.onRankingChanged(service::setRankingStrategy);
        shortlistPanel.onDetails(this::openDetails);
        shortlistPanel.onRemove(items -> items.forEach(l -> service.removeFromShortlist(l.getId())));
        shortlistPanel.onCompare(this::compare);
        shortlistPanel.onUndo(this::undo);
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = resultsTable.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    openDetails(resultsModel.getListingAt(row));
                }
            }
        });

        setLoadStatus(summary);
        service.addObserver(this);
        onCriteriaChanged(service.getCriteria());
        onResultsChanged(service.getResults());
        onShortlistChanged(service.getShortlist());
        pack();
        setLocationRelativeTo(null);
    }

    /** Creates and shows the window on the Swing event thread. */
    public static void launch(RealEstateService service, LoadSummary summary, List<String> warnings,
                              boolean aiConfigured) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Default look and feel is fine.
            }
            MainWindow w = new MainWindow(service, summary, aiConfigured);
            w.setVisible(true);
            if (!warnings.isEmpty()) {
                JOptionPane.showMessageDialog(w, String.join("\n", warnings), "Warning", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    // ---- layout ----

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem load = new JMenuItem("Load listings...");
        load.addActionListener(e -> chooseAndLoad());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> dispose());
        file.add(load);
        file.addSeparator();
        file.add(exit);
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        undoItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        undoItem.addActionListener(e -> undo());
        edit.add(undoItem);
        bar.add(edit);

        JMenu profile = new JMenu("Profile");
        JMenuItem finances = new JMenuItem("Financial profile...");
        finances.addActionListener(e -> ProfileDialog.ask(this, service.getFinancialProfile())
                .ifPresent(service::setFinancialProfile));
        profile.add(finances);
        bar.add(profile);
        return bar;
    }

    private JPanel buildCenter() {
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setRowHeight(24);
        resultsTable.setAutoCreateRowSorter(false);
        resultsTable.getTableHeader().setReorderingAllowed(false);
        styleColumns(resultsTable);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 8));
        resultCount.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
        resultsTable.setToolTipText("Double-click a listing for details, mortgage and affordability");
        panel.add(resultCount, BorderLayout.NORTH);
        panel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        return panel;
    }

    /** Right-aligns numeric columns and sets sensible widths. */
    static void styleColumns(JTable table) {
        javax.swing.table.DefaultTableCellRenderer right = new javax.swing.table.DefaultTableCellRenderer();
        right.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        int[] widths = {60, 90, 140, 110, 50, 50, 80, 80};
        for (int i = 0; i < table.getColumnCount() && i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (i >= 3) {
                table.getColumnModel().getColumn(i).setCellRenderer(right);
            }
        }
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bar.add(status, BorderLayout.WEST);
        return bar;
    }

    // ---- actions ----

    private void runSearch() {
        try {
            service.search(criteriaPanel.buildCriteria());
        } catch (InvalidCriteriaException e) {
            showError("Search not run", String.join("\n", e.getErrors()));
        } catch (IllegalArgumentException e) {
            showError("Invalid input", e.getMessage());
        }
    }

    private void chooseAndLoad() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileFilter(new FileNameExtensionFilter("Listing data (*.json, *.csv)", "json", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            LoadSummary summary = service.loadListings(chooser.getSelectedFile());
            criteriaPanel.setVocabulary(service.availableNeighborhoods(), service.availableFeatures());
            setLoadStatus(summary);
            if (summary.rejected() > 0) {
                JOptionPane.showMessageDialog(this, summary.describe() + "\n\n"
                        + String.join("\n", summary.rejections().subList(0, Math.min(10, summary.rejected()))),
                        "Some records were rejected", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException | RuntimeException e) {
            showError("Could not load listings", e.getMessage());
        }
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void setLoadStatus(LoadSummary summary) {
        loadStatus = summary.describe();
        refreshStatus();
    }

    private void refreshStatus() {
        status.setText(loadStatus + "   |   AI assistant: " + aiStatus(aiConfigured, aiFailing));
    }

    /** Status bar text for the assistant. */
    static String aiStatus(boolean configured, boolean failing) {
        if (!configured) {
            return "not configured (no API key in .env)";
        }
        return failing ? "unavailable (last request failed; search and finance tools still work)" : "ready";
    }

    private void setAiFailing(boolean failing) {
        aiFailing = failing;
        refreshStatus();
    }

    /** After each chat reply: results already arrived through the observer; open any comparison. */
    private void onAgentResponse(AgentResponse response) {
        setAiFailing(!response.isAiAvailable());
        if (response.getComparison().isPresent()) {
            new ComparisonDialog(this, response).setVisible(true);
        }
    }

    private void openDetails(Listing listing) {
        new ListingDetailDialog(this, service, listing).setVisible(true);
    }

    private void compare(List<Listing> listings) {
        List<String> ids = listings.stream().map(Listing::getId).toList();
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        new javax.swing.SwingWorker<AgentResponse, Void>() {
            @Override
            protected AgentResponse doInBackground() {
                return service.compare(ids);
            }

            @Override
            protected void done() {
                setCursor(java.awt.Cursor.getDefaultCursor());
                try {
                    AgentResponse r = get();
                    if (aiConfigured) {
                        setAiFailing(!r.isAiAvailable());
                    }
                    if (r.getComparison().isPresent()) {
                        new ComparisonDialog(MainWindow.this, r).setVisible(true);
                    } else {
                        showError("Comparison unavailable", r.getMessage());
                    }
                } catch (Exception e) {
                    showError("Comparison failed", String.valueOf(e.getCause() == null ? e : e.getCause()));
                }
            }
        }.execute();
    }

    private void undo() {
        java.util.Optional<String> undone = service.undo();
        if (undone.isEmpty()) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    // ---- observer ----

    @Override
    public void onResultsChanged(List<Listing> results) {
        onEdt(() -> {
            resultsModel.setListings(results);
            String header = results.size() + (results.size() == 1 ? " listing" : " listings")
                    + "   ·   ranked by " + service.getRankingStrategy().getName();
            String diagnosis = service.diagnoseNoResults().map(d -> d.message()).orElse(null);
            resultCount.setText(diagnosis == null ? header
                    : "<html><b>No listings match.</b> " + diagnosis + "</html>");
            resultCount.setForeground(diagnosis == null ? java.awt.Color.BLACK : new java.awt.Color(0xA66300));
        });
    }

    @Override
    public void onCriteriaChanged(SearchCriteria criteria) {
        onEdt(() -> criteriaPanel.showCriteria(criteria));
    }

    @Override
    public void onShortlistChanged(Shortlist shortlist) {
        onEdt(() -> {
            shortlistPanel.setListings(service.getShortlistedListings(), service.canUndo());
            undoItem.setEnabled(service.canUndo());
            undoItem.setText(service.canUndo() ? "Undo" : "Undo (nothing to undo)");
        });
    }

    @Override
    public void onWarning(String message) {
        onEdt(() -> JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE));
    }

    /** Runs on the Swing thread; agent searches notify from a background thread. */
    static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
