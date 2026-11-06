package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.agent.tool.ComparisonTool;
import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.ui.Formats;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Shortlist (F09) with Compare (F08). Compare is enabled for two to four selected listings.
 * The panel shows what the service reports; add, remove and undo all go through the service's
 * command invoker.
 */
final class ShortlistPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<Listing> model = new DefaultListModel<>();
    private final JList<Listing> list = new JList<>(model);
    private final JLabel title = new JLabel();
    private final JButton details = new JButton("Details");
    private final JButton remove = new JButton("Remove");
    private final JButton compare = new JButton("Compare");
    private final JButton undo = new JButton("Undo");

    ShortlistPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 8));
        setPreferredSize(new Dimension(250, 400));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 13f));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                Listing x = (Listing) v;
                String text = "<html><b>" + x.getId() + "</b> · " + Formats.money(x.getPrice()) + "<br><small>"
                        + Formats.type(x) + ", " + x.getNeighborhood() + ", " + x.getBedrooms() + " bed</small></html>";
                Component c = super.getListCellRendererComponent(l, text, i, sel, focus);
                ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                return c;
            }
        });
        list.addListSelectionListener(e -> updateButtons());
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
        buttons.add(details);
        buttons.add(remove);
        buttons.add(compare);
        buttons.add(undo);
        JLabel hint = new JLabel("<html><small>Select 2-4 to compare. Double-click a result to see details and shortlist it.</small></html>");
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(buttons, BorderLayout.CENTER);
        south.add(hint, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        setListings(List.of(), false);
    }

    void setListings(List<Listing> listings, boolean canUndo) {
        model.clear();
        listings.forEach(model::addElement);
        title.setText("Shortlist (" + listings.size() + ")");
        undo.setEnabled(canUndo);
        updateButtons();
    }

    private void updateButtons() {
        int n = list.getSelectedIndices().length;
        details.setEnabled(n == 1);
        remove.setEnabled(n >= 1);
        compare.setEnabled(n >= ComparisonTool.MIN_LISTINGS && n <= ComparisonTool.MAX_LISTINGS);
        compare.setToolTipText(n > ComparisonTool.MAX_LISTINGS ? "At most four can be compared" : null);
    }

    List<Listing> selected() {
        return list.getSelectedValuesList();
    }

    void onDetails(Consumer<Listing> action) {
        details.addActionListener(e -> selected().stream().findFirst().ifPresent(action));
    }

    void onRemove(Consumer<List<Listing>> action) {
        remove.addActionListener(e -> action.accept(selected()));
    }

    void onCompare(Consumer<List<Listing>> action) {
        compare.addActionListener(e -> action.accept(selected()));
    }

    void onUndo(Runnable action) {
        undo.addActionListener(e -> action.run());
    }

    // For tests.
    JList<Listing> listView() {
        return list;
    }

    boolean compareEnabled() {
        return compare.isEnabled();
    }

    boolean undoEnabled() {
        return undo.isEnabled();
    }
}
