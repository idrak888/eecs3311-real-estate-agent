package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.agent.AgentResponse;
import ca.yorku.realestate.agent.tool.ComparisonResult;
import ca.yorku.realestate.domain.Listing;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Side-by-side comparison (F08): the deterministic matrix with the best value per row marked,
 * and the AI summary underneath when available.
 */
final class ComparisonDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    static final String LEADER_MARK = "★ ";

    ComparisonDialog(Window owner, AgentResponse response) {
        super(owner, "Compare properties", ModalityType.MODELESS);
        ComparisonResult c = response.getComparison().orElseThrow();
        Model model = new Model(c);
        JTable table = new JTable(model);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new LeaderRenderer(model));
        table.getColumnModel().getColumn(0).setPreferredWidth(130);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        String summary = summaryText(response);
        if (!summary.isEmpty()) {
            JTextArea text = new JTextArea(summary);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setEditable(false);
            text.setRows(5);
            text.setBorder(BorderFactory.createTitledBorder("Advisor's summary"));
            south.add(text, BorderLayout.CENTER);
        }
        JLabel note = new JLabel(LEADER_MARK + "= best in row" + response.getNotice().map(n -> "    " + n).orElse(""));
        note.setForeground(Color.GRAY);
        south.add(note, BorderLayout.NORTH);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel();
        buttons.add(close);
        south.add(buttons, BorderLayout.SOUTH);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(new Dimension(Math.max(560, 180 + 170 * c.getListings().size()), 560));
        setLocationRelativeTo(owner);
    }

    /** The agent's message without the "Comparing ..." header line. */
    static String summaryText(AgentResponse r) {
        String m = r.getMessage();
        int split = m.indexOf("\n\n");
        return split < 0 ? "" : m.substring(split + 2).trim();
    }

    /** Rows are attributes; columns are the listings. */
    static final class Model extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final transient ComparisonResult c;

        Model(ComparisonResult c) {
            this.c = c;
        }

        @Override
        public int getRowCount() {
            return c.getAttributeNames().size();
        }

        @Override
        public int getColumnCount() {
            return 1 + c.getListings().size();
        }

        @Override
        public String getColumnName(int col) {
            return col == 0 ? "Attribute" : c.getListings().get(col - 1).getId();
        }

        @Override
        public Object getValueAt(int row, int col) {
            String attribute = c.getAttributeNames().get(row);
            if (col == 0) {
                return attribute;
            }
            String cell = ComparisonResult.format(attribute, c.getAttribute(attribute).get(col - 1));
            return isLeader(row, col) ? LEADER_MARK + cell : cell;
        }

        boolean isLeader(int row, int col) {
            if (col == 0) {
                return false;
            }
            Listing l = c.getListings().get(col - 1);
            return l.getId().equals(c.getLeader(c.getAttributeNames().get(row)));
        }
    }

    private static final class LeaderRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final transient Model model;

        LeaderRenderer(Model model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            Component comp = super.getTableCellRendererComponent(t, v, sel, focus, row, col);
            boolean leader = model.isLeader(row, col);
            comp.setFont(comp.getFont().deriveFont(leader || col == 0 ? Font.BOLD : Font.PLAIN));
            if (!sel) {
                comp.setBackground(leader ? new Color(0xE6F4EA) : Color.WHITE);
                comp.setForeground(col == 0 ? Color.DARK_GRAY : Color.BLACK);
            }
            return comp;
        }
    }
}
