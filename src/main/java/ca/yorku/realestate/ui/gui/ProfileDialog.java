package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.domain.finance.FinancialProfile;
import ca.yorku.realestate.ui.Formats;
import java.awt.Component;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Prompts for the buyer's finances (F07). Re-asks with a message until the input is valid or cancelled. */
final class ProfileDialog {

    private ProfileDialog() {
    }

    static Optional<FinancialProfile> ask(Component owner, Optional<FinancialProfile> existing) {
        JTextField income = new JTextField(existing.map(p -> plain(p.getAnnualIncome())).orElse(""), 12);
        JTextField debt = new JTextField(existing.map(p -> plain(p.getMonthlyDebt())).orElse(""), 12);
        JTextField down = new JTextField(existing.map(p -> plain(p.getDownPayment())).orElse(""), 12);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("Gross annual income"));
        form.add(income);
        form.add(new JLabel("Monthly debt payments"));
        form.add(debt);
        form.add(new JLabel("Down payment available"));
        form.add(down);
        String error = null;
        while (true) {
            Object message = error == null ? form : new Object[] {error, form};
            int choice = JOptionPane.showConfirmDialog(owner, message, "Financial profile",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            try {
                return Optional.of(parse(income.getText(), debt.getText(), down.getText()));
            } catch (IllegalArgumentException e) {
                error = e.getMessage();
            }
        }
    }

    /** Parses the three fields; amounts accept 120000, 120,000 or 120k. */
    static FinancialProfile parse(String income, String debt, String down) {
        return new FinancialProfile(amount(income, "Annual income"),
                debt == null || debt.isBlank() ? 0 : amount(debt, "Monthly debt"),
                amount(down, "Down payment"));
    }

    private static double amount(String text, String name) {
        try {
            return Formats.parseMoney(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(name + ": enter an amount like 120000 or 120k");
        }
    }

    private static String plain(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }
}
