package ca.yorku.realestate.agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry and dispatcher for agent tools. The agent asks for a tool by name; unknown names
 * and unexpected exceptions become failed results, so one bad tool call never crashes a turn.
 */
public class ToolManager {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /** @throws IllegalArgumentException if a tool with the same name is already registered */
    public void register(AgentTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool is required");
        }
        if (tools.containsKey(tool.getName())) {
            throw new IllegalArgumentException("A tool named " + tool.getName() + " is already registered");
        }
        tools.put(tool.getName(), tool);
    }

    public ToolResult execute(String name, ToolRequest request) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failure("Unknown tool: " + name);
        }
        try {
            return tool.execute(request == null ? ToolRequest.empty() : request);
        } catch (IllegalArgumentException e) {
            return ToolResult.failure(e.getMessage());
        } catch (RuntimeException e) {
            return ToolResult.failure("The " + name + " tool failed: " + e.getMessage());
        }
    }

    public Optional<AgentTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** Registered tool names, in registration order. */
    public List<String> listAvailable() {
        return new ArrayList<>(tools.keySet());
    }

    /** "name: description" per line, for the tool selection prompt. */
    public String describeTools() {
        StringBuilder sb = new StringBuilder();
        for (AgentTool t : tools.values()) {
            sb.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append('\n');
        }
        return sb.toString();
    }
}
