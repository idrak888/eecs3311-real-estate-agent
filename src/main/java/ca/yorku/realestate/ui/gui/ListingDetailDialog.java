package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.domain.finance.AffordabilityResult;
import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.domain.finance.MortgageResult;
import ca.yorku.realestate.domain.finance.MortgageTerms;
import ca.yorku.realestate.domain.finance.Verdict;
import ca.yorku.realestate.service.RealEstateService;
import ca.yorku.realestate.ui.Formats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

/**
 * Listing details (F05) with mortgage (F06) and affordability (F07) tabs. Every figure comes from
 * the service; the dialog only formats.
 */
final class ListingDetailDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final transient RealEstateService service;
    private final transient Listing listing;
    private final JButton shortlistButton = new JButton();

    private final JTextField down = new JTextField(10);
    private final JTextField rate = new JTextField(6);
    private final JTextField years = new JTextField(4);
    private final JLabel payment = new JLabel(" ");
    private final JLabel principal = new JLabel(" ");
    private final JLabel interest = new JLabel(" ");
    private final JLabel totalCost = new JLabel(" ");
    private final JLabel mortgageError = new JLabel(" ");
    private final JPanel affordPanel = new JPanel(new BorderLayout());

    ListingDetailDialog(Window owner, RealEstateService service, Listing listing) {
        super(owner, listing.getId() + " - " + Formats.type(listing) + " in " + listing.getNeighborhood(),
                ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.listing = listing;

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Details", detailsTab());
        tabs.addTab("Mortgage", mortgageTab());
        tabs.addTab("Affordability", affordPanel);
        refreshAffordability();
        add(tabs, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        refreshShortlistButton();
        shortlistButton.addActionListener(e -> toggleShortlist());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(shortlistButton);
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);

        setSize(520, 420);
        setLocationRelativeTo(owner);
    }

    private JPanel detailsTab() {
        Grid g = new Grid();
        g.row("Price", Formats.money(listing.getPrice()));
        g.row("Type", Formats.type(listing));
        g.row("Neighborhood", listing.getNeighborhood());
        g.row("Bedrooms", String.valueOf(listing.getBedrooms()));
        g.row("Bathrooms", String.valueOf(listing.getBathrooms()));
        g.row("Square feet", Formats.squareFeet(listing));
        g.row("Price per sq ft", Formats.pricePerSqFt(listing));
        g.row("Year built", String.valueOf(listing.getYearBuilt()));
        g.row("Features", listing.getFeatures().isEmpty() ? "none" : String.join(", ", listing.getFeatures()));
        MortgageResult m = service.calculateMortgage(listing.getId(), null);
        g.row("Est. payment", Formats.moneyCents(m.getMonthlyPayment()) + " / month  (20% down, "
                + MortgageTerms.DEFAULT_ANNUAL_RATE + "%, " + MortgageTerms.DEFAULT_YEARS + " yrs)");
        return g.panel;
    }

    private JPanel mortgageTab() {
        MortgageTerms d = MortgageTerms.defaultsFor(listing.getPrice());
        down.setText(String.format(Locale.ROOT, "%.0f", d.getDownPayment()));
        rate.setText(String.valueOf(d.getAnnualRate()));
        years.setText(String.valueOf(d.getYears()));
        Grid g = new Grid();
        g.row("Down payment", down);
        g.row("Annual rate (%)", rate);
        g.row("Amortization (years)", years);
        JButton calculate = new JButton("Calculate");
        calculate.addActionListener(e -> calculate());
        g.row("", calculate);
        g.row("Monthly payment", payment);
        g.row("Amount borrowed", principal);
        g.row("Total interest", interest);
        g.row("Total cost", totalCost);
        mortgageError.setForeground(new Color(0xB00020));
        g.row("", mortgageError);
        calculate();
        return g.panel;
    }

    private void calculate() {
        try {
            MortgageTerms terms = new MortgageTerms(Formats.parseMoney(down.getText()),
                    Double.parseDouble(rate.getText().trim().replace("%", "")),
                    Integer.parseInt(years.getText().trim()));
            MortgageResult m = service.calculateMortgage(listing.getId(), terms);
            mortgageError.setText(" ");
            if (!m.isFinancingRequired()) {
                payment.setText("No mortgage needed: the down payment covers the price");
                principal.setText("$0");
                interest.setText("$0");
                totalCost.setText(Formats.money(m.getTotalCost()));
                return;
            }
            payment.setText(Formats.moneyCents(m.getMonthlyPayment()));
            principal.setText(Formats.money(m.getPrincipal()) + "  (LTV " + Formats.percent(m.getLoanToValue()) + ")");
            interest.setText(Formats.money(m.getTotalInterest()));
            totalCost.setText(Formats.money(m.getTotalCost()));
        } catch (NumberFormatException e) {
            mortgageError.setText("Rate and years must be numbers.");
        } catch (IllegalArgumentException e) {
            mortgageError.setText(e.getMessage());
        }
    }

    private void refreshAffordability() {
        affordPanel.removeAll();
        Optional<FinancialProfile> profile = service.getFinancialProfile();
        JButton edit = new JButton(profile.isPresent() ? "Edit financial profile..." : "Enter financial profile...");
        edit.addActionListener(e -> ProfileDialog.ask(this, service.getFinancialProfile()).ifPresent(p -> {
            service.setFinancialProfile(p);
            refreshAffordability();
        }));
        if (profile.isEmpty()) {
            JLabel prompt = new JLabel("<html>Enter your income, monthly debt and down payment to see whether "
                    + "this property is affordable. Nothing is assumed.</html>");
            prompt.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            affordPanel.add(prompt, BorderLayout.CENTER);
        } else {
            AffordabilityResult a = service.assessAffordability(listing.getId(), null);
            Grid g = new Grid();
            JLabel verdict = new JLabel(a.getVerdict().getLabel());
            verdict.setFont(verdict.getFont().deriveFont(Font.BOLD, 16f));
            verdict.setForeground(color(a.getVerdict()));
            g.row("Verdict", verdict);
            g.row("", new JLabel("<html><div style='width:240px'>" + a.getExplanation() + "</div></html>"));
            g.row("Monthly housing cost", Formats.moneyCents(a.getMonthlyHousingCost()));
            g.row("GDS ratio", Formats.percent(a.getGdsRatio()) + "  (limit 39%)");
            g.row("TDS ratio", Formats.percent(a.getTdsRatio()) + "  (limit 44%)");
            g.row("Max supportable price", Formats.money(a.getMaxPrice()));
            affordPanel.add(g.panel, BorderLayout.CENTER);
        }
        JPanel south = new JPanel();
        south.add(edit);
        affordPanel.add(south, BorderLayout.SOUTH);
        affordPanel.revalidate();
        affordPanel.repaint();
    }

    static Color color(Verdict v) {
        return switch (v) {
            case AFFORDABLE -> new Color(0x1E6B3A);
            case MARGINAL -> new Color(0xA66300);
            case UNAFFORDABLE -> new Color(0xB00020);
        };
    }

    private void toggleShortlist() {
        if (service.getShortlist().contains(listing.getId())) {
            service.removeFromShortlist(listing.getId());
        } else {
            service.addToShortlist(listing.getId());
        }
        refreshShortlistButton();
    }

    private void refreshShortlistButton() {
        shortlistButton.setText(service.getShortlist().contains(listing.getId())
                ? "Remove from shortlist" : "Add to shortlist");
    }

    /** Two-column label/value grid. */
    private static final class Grid {
        final JPanel panel = new JPanel(new GridBagLayout());
        private int row;

        Grid() {
            panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        }

        void row(String label, String value) {
            row(label, new JLabel(value));
        }

        void row(String label, java.awt.Component value) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row++;
            c.insets = new Insets(3, 0, 3, 12);
            c.anchor = GridBagConstraints.WEST;
            c.gridx = 0;
            JLabel l = new JLabel(label);
            l.setForeground(Color.GRAY);
            panel.add(l, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.NONE;
            panel.add(value, c);
        }
    }
}
