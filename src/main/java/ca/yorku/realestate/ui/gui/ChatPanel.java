package ca.yorku.realestate.ui.gui;

import ca.yorku.realestate.agent.AgentResponse;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Chat with the advisor (F02, F10). Each message runs on a background thread so the window stays
 * responsive while the model works; the send box is disabled until the reply arrives.
 *
 * <p>The panel only displays: the agent is reached through the function given to the
 * constructor (the service's {@code askAgent}), and any search the agent runs reaches the
 * results table and criteria panel through the service's observers.
 */
public final class ChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Function<String, AgentResponse> ask;
    private final transient Consumer<AgentResponse> onResponse;
    private final JTextPane transcript = new JTextPane();
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("Send");
    private final JLabel status = new JLabel(" ");

    private final SimpleAttributeSet youStyle = style(new Color(0x1F4E8C), true, false, 13);
    private final SimpleAttributeSet advisorStyle = style(new Color(0x1E6B3A), true, false, 13);
    private final SimpleAttributeSet bodyStyle = style(Color.DARK_GRAY, false, false, 13);
    private final SimpleAttributeSet noteStyle = style(Color.GRAY, false, true, 12);

    /**
     * @param ask sends a message to the agent; called off the Swing thread
     * @param onResponse called on the Swing thread after each reply, e.g. to open a comparison
     */
    public ChatPanel(Function<String, AgentResponse> ask, Consumer<AgentResponse> onResponse, boolean aiConfigured) {
        super(new BorderLayout(0, 4));
        this.ask = ask;
        this.onResponse = onResponse;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 4, 8, 8),
                BorderFactory.createTitledBorder("Advisor chat")));

        transcript.setEditable(false);
        transcript.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(new JScrollPane(transcript), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        input.setToolTipText("e.g. 3 bedroom house under 900k with a garage");
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        status.setFont(status.getFont().deriveFont(Font.ITALIC, 11f));
        bottom.add(status, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        send.addActionListener(e -> submit());
        input.addActionListener(e -> submit());

        if (aiConfigured) {
            appendNote("Describe what you're looking for, e.g. \"3 bedroom house under $900k, ideally near a school\". "
                    + "Ask follow-ups like \"what would the second one cost per month?\" or \"compare the top two\".");
        } else {
            appendNote("The AI assistant is not configured (no API key in .env). Chat is unavailable, "
                    + "but search, ranking, details, mortgage, affordability, shortlist and comparison all work.");
            input.setEnabled(false);
            send.setEnabled(false);
        }
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty() || !send.isEnabled()) {
            return;
        }
        input.setText("");
        appendYou(text);
        setBusy(true);
        new SwingWorker<AgentResponse, Void>() {
            @Override
            protected AgentResponse doInBackground() {
                return ask.apply(text);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    AgentResponse r = get();
                    show(r);
                    onResponse.accept(r);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    appendNote("Something went wrong: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        input.setEnabled(!busy);
        send.setEnabled(!busy);
        status.setText(busy ? "Advisor is thinking..." : " ");
        if (!busy) {
            input.requestFocusInWindow();
        }
    }

    /** Appends an agent reply. Package-private so the window can show direct comparisons too. */
    void show(AgentResponse r) {
        append("Advisor: ", advisorStyle);
        append(r.getMessage() + "\n", bodyStyle);
        r.getNotice().ifPresent(n -> append("  " + n + "\n", noteStyle));
        String meta = metaLine(r);
        if (!meta.isEmpty()) {
            append("  " + meta + "\n", noteStyle);
        }
        append("\n", bodyStyle);
    }

    /** Small grey line under a reply: which tools ran and what the user can do next. */
    static String metaLine(AgentResponse r) {
        StringBuilder sb = new StringBuilder();
        if (!r.getToolsUsed().isEmpty()) {
            sb.append("tools: ").append(String.join(", ", r.getToolsUsed()));
        }
        if (r.getToolsUsed().contains("search") && !r.getListings().isEmpty()) {
            sb.append(sb.length() > 0 ? " · " : "").append("results updated above");
        }
        if (r.getComparison().isPresent()) {
            sb.append(sb.length() > 0 ? " · " : "").append("comparison opened");
        }
        return sb.toString();
    }

    private void appendYou(String text) {
        append("You: ", youStyle);
        append(text + "\n\n", bodyStyle);
    }

    void appendNote(String text) {
        append(text + "\n\n", noteStyle);
    }

    private void append(String text, SimpleAttributeSet attrs) {
        StyledDocument doc = transcript.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, attrs);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        transcript.setCaretPosition(doc.getLength());
    }

    /** The transcript text, for tests. */
    String transcriptText() {
        return transcript.getText();
    }

    private static SimpleAttributeSet style(Color color, boolean bold, boolean italic, int size) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        StyleConstants.setFontSize(s, size);
        return s;
    }
}
