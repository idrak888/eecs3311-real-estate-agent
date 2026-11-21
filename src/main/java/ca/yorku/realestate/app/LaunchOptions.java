package ca.yorku.realestate.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Command-line arguments of {@link Main}. Options may be written with one or two dashes
 * ({@code -input} or {@code --input}) and are case-insensitive ({@code -LLMAPI} = {@code -llmapi}).
 *
 * <p>Modes, in order of precedence:
 * <ul>
 *   <li>{@code -input task.txt}: run the agent on the natural-language task in the file, print the
 *       answers and exit. Blank lines separate turns, so a file can hold follow-up requests.</li>
 *   <li>{@code -script commands.txt}: run CLI commands from a file and exit.</li>
 *   <li>{@code -c "command"} (repeatable): run CLI commands given as arguments and exit.</li>
 *   <li>{@code -cli}: interactive CLI reading standard input (keyboard, pipe or redirect).</li>
 *   <li>no mode option: the Swing GUI.</li>
 * </ul>
 * Other options: {@code -LLMAPI [anthropic]} requires the LLM API and fails fast when no key is
 * configured; {@code -model NAME} overrides the model; {@code -output FILE} also writes the
 * transcript to a file; {@code -data FILE} loads a different listing file; {@code -help}.
 */
public record LaunchOptions(boolean cli, Optional<File> data, Optional<File> script, List<String> commands,
                            Optional<File> input, Optional<File> output, boolean requireLlm,
                            Optional<String> model, boolean help) {

    public static final String USAGE = String.join("\n",
            "Usage: java -jar Agent.jar [options]",
            "  (no options)            start the GUI",
            "  -cli                    interactive CLI; reads commands from standard input",
            "  -input FILE             run the agent on the natural-language task in FILE and exit",
            "                          (blank lines separate follow-up requests; # starts a comment)",
            "  -script FILE, -f FILE   run CLI commands from FILE and exit",
            "  -c \"COMMAND\"            run one CLI command and exit (repeatable)",
            "  -LLMAPI [anthropic]     require the LLM API; exit with code 3 if no API key is configured",
            "  -model NAME             LLM model (default from .env LLM_MODEL, else claude-sonnet-5-5)",
            "  -output FILE            also write the transcript to FILE",
            "  -data FILE              load listings from FILE (.json or .csv) instead of the bundled data",
            "  -help                   show this help",
            "The API key is read from LLM_API_KEY in .env (working directory) or the environment.");

    public LaunchOptions {
        commands = List.copyOf(commands);
    }

    /** True when the CLI runs a task, a script or commands and then exits. */
    public boolean batch() {
        return input.isPresent() || script.isPresent() || !commands.isEmpty();
    }

    /**
     * Parses the arguments.
     *
     * @throws IllegalArgumentException for an unknown option, an option missing its value, or a
     *     conflicting combination
     */
    public static LaunchOptions parse(String... args) {
        boolean cli = false;
        boolean requireLlm = false;
        boolean help = false;
        File data = null;
        File script = null;
        File input = null;
        File output = null;
        String model = null;
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (normalize(arg)) {
                case "cli" -> cli = true;
                case "data" -> data = new File(value(args, ++i, arg));
                case "script", "f" -> {
                    script = new File(value(args, ++i, arg));
                    cli = true;
                }
                case "command", "c" -> {
                    commands.add(value(args, ++i, arg));
                    cli = true;
                }
                case "input", "i", "task" -> {
                    input = new File(value(args, ++i, arg));
                    cli = true;
                }
                case "output", "o" -> output = new File(value(args, ++i, arg));
                case "llmapi", "llm-api", "llm" -> {
                    requireLlm = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        String provider = args[++i].toLowerCase(Locale.ROOT);
                        if (!provider.equals("anthropic") && !provider.equals("claude")) {
                            throw new IllegalArgumentException("Unsupported LLM API '" + args[i]
                                    + "'. Supported: anthropic.\n" + USAGE);
                        }
                    }
                }
                case "model" -> model = value(args, ++i, arg);
                case "help", "h", "?" -> help = true;
                default -> throw new IllegalArgumentException("Unknown option '" + arg + "'.\n" + USAGE);
            }
        }
        int modes = (input != null ? 1 : 0) + (script != null ? 1 : 0) + (commands.isEmpty() ? 0 : 1);
        if (modes > 1) {
            throw new IllegalArgumentException("Use only one of -input, -script and -c.\n" + USAGE);
        }
        return new LaunchOptions(cli, Optional.ofNullable(data), Optional.ofNullable(script), commands,
                Optional.ofNullable(input), Optional.ofNullable(output), requireLlm,
                Optional.ofNullable(model), help);
    }

    private static String normalize(String arg) {
        if (arg.startsWith("--")) {
            return arg.substring(2).toLowerCase(Locale.ROOT);
        }
        if (arg.startsWith("-")) {
            return arg.substring(1).toLowerCase(Locale.ROOT);
        }
        return "\0" + arg;
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length || (args[index].startsWith("-") && args[index].length() > 1)) {
            throw new IllegalArgumentException(option + " needs a value.\n" + USAGE);
        }
        return args[index];
    }

    /**
     * Splits a task file into agent turns: blocks separated by blank lines, lines inside a block
     * joined with spaces, lines starting with {@code #} ignored.
     */
    public static List<String> taskTurns(List<String> lines) {
        List<String> turns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.startsWith("#")) {
                continue;
            }
            if (line.isEmpty()) {
                if (current.length() > 0) {
                    turns.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            turns.add(current.toString());
        }
        return turns;
    }
}
