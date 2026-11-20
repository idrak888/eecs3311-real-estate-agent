package ca.yorku.realestate.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Command-line arguments of {@link Main}.
 *
 * <p>The CLI accepts input four ways:
 * <ul>
 *   <li>keyboard: {@code --cli}</li>
 *   <li>standard input from a pipe or redirect: {@code --cli < commands.txt}</li>
 *   <li>a command file: {@code --script commands.txt}</li>
 *   <li>one-shot commands: {@code --command "ask 3 bed house under 900k"} (repeatable)</li>
 * </ul>
 * {@code --script} and {@code --command} imply {@code --cli}. {@code --data <file>} loads a
 * different listing file in any mode.
 */
public record LaunchOptions(boolean cli, Optional<File> data, Optional<File> script, List<String> commands) {

    public LaunchOptions {
        commands = List.copyOf(commands);
    }

    /** True when the CLI runs a script or one-shot commands instead of reading standard input. */
    public boolean batch() {
        return script.isPresent() || !commands.isEmpty();
    }

    /**
     * Parses the arguments.
     *
     * @throws IllegalArgumentException for an unknown option or an option missing its value
     */
    public static LaunchOptions parse(String... args) {
        boolean cli = false;
        File data = null;
        File script = null;
        List<String> commands = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--cli" -> cli = true;
                case "--data" -> data = new File(value(args, ++i, arg));
                case "--script", "-f" -> {
                    script = new File(value(args, ++i, arg));
                    cli = true;
                }
                case "--command", "-c" -> {
                    commands.add(value(args, ++i, arg));
                    cli = true;
                }
                default -> throw new IllegalArgumentException("Unknown option '" + arg + "'. " + USAGE);
            }
        }
        if (script != null && !commands.isEmpty()) {
            throw new IllegalArgumentException("Use either --script or --command, not both. " + USAGE);
        }
        return new LaunchOptions(cli, Optional.ofNullable(data), Optional.ofNullable(script), commands);
    }

    public static final String USAGE = "Usage: Main [--cli] [--script FILE | --command \"CMD\"...] [--data FILE]";

    private static String value(String[] args, int index, String option) {
        if (index >= args.length || args[index].startsWith("--")) {
            throw new IllegalArgumentException(option + " needs a value. " + USAGE);
        }
        return args[index];
    }
}
