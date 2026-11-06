package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.domain.PropertyType;
import ca.yorku.realestate.domain.SearchCriteria;
import ca.yorku.realestate.domain.SearchCriteriaBuilder;
import ca.yorku.realestate.domain.ranking.RankingStrategy;
import ca.yorku.realestate.ui.Formats;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * Structured search controls (F03) and the ranking dropdown (F04).
 *
 * <p>The panel only converts controls to and from {@link SearchCriteria}; searching is done by
 * the window through the service. {@link #showCriteria} fills the controls from criteria, which
 * is how the panel reflects what the agent extracted from a chat message (F02).
 */
public final class CriteriaPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JTextField minPrice = new JTextField(9);
    private final JTextField maxPrice = new JTextField(9);
    private final JTextField minBeds = new JTextField(4);
    private final JTextField minBaths = new JTextField(4);
    private final transient Map<PropertyType, JCheckBox> types = new EnumMap<>(PropertyType.class);
    private final DefaultListModel<String> neighborhoodModel = new DefaultListModel<>();
    private final JList<String> neighborhoods = new JList<>(neighborhoodModel);
    private final DefaultListModel<String> featureModel = new DefaultListModel<>();
    private final JList<String> features = new JList<>(featureModel);
    private final JComboBox<String> ranking = new JComboBox<>();
    private final JButton searchButton = new JButton("Search");
    private final JButton clearButton = new JButton("Clear");
    private final JLabel summary = new JLabel(" ");

    public CriteriaPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 2, 3, 2);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        row = heading(form, c, row, "Search criteria");
        row = field(form, c, row, "Min price", minPrice);
        row = field(form, c, row, "Max price", maxPrice);
        row = field(form, c, row, "Min bedrooms", minBeds);
        row = field(form, c, row, "Min bathrooms", minBaths);

        JPanel typeBox = new JPanel(new java.awt.GridLayout(0, 1));
        for (PropertyType t : PropertyType.values()) {
            String name = t.name().charAt(0) + t.name().substring(1).toLowerCase(Locale.ROOT);
            JCheckBox box = new JCheckBox(name);
            types.put(t, box);
            typeBox.add(box);
        }
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        form.add(new JLabel("Type"), c);
        c.gridy = row++;
        form.add(typeBox, c);

        neighborhoods.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        neighborhoods.setVisibleRowCount(6);
        features.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        features.setVisibleRowCount(6);
        c.gridy = row++;
        form.add(new JLabel("Neighborhoods (any of)"), c);
        c.gridy = row++;
        form.add(listScroll(neighborhoods), c);
        c.gridy = row++;
        form.add(new JLabel("Required features (all of)"), c);
        c.gridy = row++;
        form.add(listScroll(features), c);

        JPanel buttons = new JPanel();
        buttons.add(searchButton);
        buttons.add(clearButton);
        c.gridy = row++;
        form.add(buttons, c);

        row = heading(form, c, row, "Rank results by");
        for (String key : Formats.strategies().keySet()) {
            ranking.addItem(Formats.strategies().get(key).getName());
        }
        c.gridy = row++;
        form.add(ranking, c);

        summary.setFont(summary.getFont().deriveFont(11f));
        summary.setPreferredSize(new Dimension(220, 48));
        summary.setVerticalAlignment(JLabel.TOP);
        c.gridy = row;
        form.add(summary, c);

        add(form, BorderLayout.NORTH);
    }

    private static JScrollPane listScroll(JList<String> list) {
        JScrollPane scroll = new JScrollPane(list);
        Dimension size = new Dimension(220, 120);
        scroll.setPreferredSize(size);
        scroll.setMinimumSize(size);
        return scroll;
    }

    private static int heading(JPanel form, GridBagConstraints c, int row, String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD, 13f));
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.insets = new Insets(row == 0 ? 0 : 12, 2, 4, 2);
        form.add(label, c);
        c.insets = new Insets(3, 2, 3, 2);
        return row + 1;
    }

    private static int field(JPanel form, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
        c.weightx = 0;
        return row + 1;
    }

    /** Fills the neighborhood and feature lists from the loaded data. */
    public void setVocabulary(List<String> neighborhoodNames, List<String> featureNames) {
        neighborhoodModel.clear();
        neighborhoodNames.forEach(neighborhoodModel::addElement);
        featureModel.clear();
        featureNames.forEach(featureModel::addElement);
    }

    /**
     * Builds criteria from the controls. Blank fields are unconstrained.
     *
     * @throws IllegalArgumentException with a readable message for a malformed amount or count
     */
    public SearchCriteria buildCriteria() {
        SearchCriteriaBuilder b = new SearchCriteriaBuilder()
                .withMinPrice(money(minPrice, "Min price"))
                .withMaxPrice(money(maxPrice, "Max price"))
                .withMinBedrooms(count(minBeds, "Min bedrooms"))
                .withMinBathrooms(count(minBaths, "Min bathrooms"));
        types.forEach((t, box) -> {
            if (box.isSelected()) {
                b.withType(t);
            }
        });
        neighborhoods.getSelectedValuesList().forEach(b::withNeighborhood);
        features.getSelectedValuesList().forEach(b::withFeature);
        return b.build();
    }

    /** Shows criteria in the controls, e.g. after the agent interpreted a chat message. */
    public void showCriteria(SearchCriteria criteria) {
        SearchCriteria c = criteria == null ? SearchCriteria.empty() : criteria;
        minPrice.setText(c.getMinPrice() == null ? "" : String.format(Locale.ROOT, "%.0f", c.getMinPrice()));
        maxPrice.setText(c.getMaxPrice() == null ? "" : String.format(Locale.ROOT, "%.0f", c.getMaxPrice()));
        minBeds.setText(c.getMinBedrooms() == null ? "" : c.getMinBedrooms().toString());
        minBaths.setText(c.getMinBathrooms() == null ? "" : c.getMinBathrooms().toString());
        types.forEach((t, box) -> box.setSelected(c.getTypes().contains(t)));
        select(neighborhoods, neighborhoodModel, c.getNeighborhoods());
        select(features, featureModel, c.getRequiredFeatures());
        summary.setText("<html><i>" + (c.isEmpty() ? "No criteria: showing all listings" : c.describe()) + "</i></html>");
    }

    private static void select(JList<String> list, DefaultListModel<String> model, java.util.Collection<String> wanted) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            String item = model.get(i);
            for (String w : wanted) {
                if (item.equalsIgnoreCase(w)) {
                    indices.add(i);
                }
            }
        }
        list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
    }

    public void clear() {
        showCriteria(SearchCriteria.empty());
    }

    /** The ranking strategy chosen in the dropdown. */
    public RankingStrategy selectedStrategy() {
        int i = Math.max(0, ranking.getSelectedIndex());
        return new ArrayList<>(Formats.strategies().values()).get(i);
    }

    public void onSearch(Runnable action) {
        searchButton.addActionListener(e -> action.run());
    }

    public void onClear(Runnable action) {
        clearButton.addActionListener(e -> action.run());
    }

    public void onRankingChanged(Consumer<RankingStrategy> action) {
        ranking.addActionListener(e -> action.accept(selectedStrategy()));
    }

    // Package-private access for tests.
    JTextField minPriceField() {
        return minPrice;
    }

    JTextField maxPriceField() {
        return maxPrice;
    }

    JTextField minBedsField() {
        return minBeds;
    }

    JCheckBox typeBox(PropertyType t) {
        return types.get(t);
    }

    JList<String> neighborhoodList() {
        return neighborhoods;
    }

    private static Double money(JTextField f, String name) {
        String t = f.getText().trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Formats.parseMoney(t);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + ": enter an amount like 850000 or 850k");
        }
    }

    private static Integer count(JTextField f, String name) {
        String t = f.getText().trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(t);
            if (v < 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + ": enter a whole number");
        }
    }
}
