package ca.yorku.realestate.agent.tool;

/**
 * A capability the agent can invoke. Tools are deterministic: they compute or retrieve and
 * return values the agent may describe but never produces itself. The name and description
 * are shown to the model when it chooses which tool a request needs.
 */
public interface AgentTool {

    /** Short identifier used in tool selection, e.g. "search". */
    String getName();

    /** One-line description of what the tool does and the parameters it takes. */
    String getDescription();

    /**
     * Runs the tool. Expected problems (unknown listing, invalid input) come back as a failed
     * {@link ToolResult} rather than an exception.
     */
    ToolResult execute(ToolRequest request);
}
