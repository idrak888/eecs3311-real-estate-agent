package ca.yorku.realestate.app;

import ca.yorku.realestate.agent.llm.AnthropicLLMClient;
import ca.yorku.realestate.agent.llm.LLMClient;
import ca.yorku.realestate.config.EnvConfig;
import ca.yorku.realestate.persistence.LoadSummary;
import ca.yorku.realestate.persistence.PreferenceStore;
import ca.yorku.realestate.persistence.parser.UnsupportedFormatException;
import ca.yorku.realestate.service.RealEstateService;
import ca.yorku.realestate.ui.cli.CLIRenderer;
import ca.yorku.realestate.ui.gui.MainWindow;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * Application entry point. Starts the GUI by default; the options in {@link LaunchOptions} run the
 * CLI instead, including standalone runs such as
 * {@code java -jar Agent.jar -LLMAPI -input task.txt}. Every mode uses the same
 * {@link RealEstateService}, wired with the agent and the API key from {@code .env} or the
 * environment.
 *
 * <p>Exit codes in CLI modes: 0 success; 1 the AI could not answer a task turn; 2 bad arguments or
 * an unreadable file; 3 {@code -LLMAPI} given but no API key configured.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.println(LaunchOptions.USAGE);
            return;
        }
        for (Optional<File> f : List.of(options.script(), options.input(), options.data())) {
            if (f.isPresent() && !f.get().isFile()) {
                System.err.println("File not found: " + f.get());
                System.exit(2);
                return;
            }
        }

        EnvConfig config = EnvConfig.load();
        AnthropicLLMClient llm = new AnthropicLLMClient(config.apiKey().orElse(null),
                options.model().orElse(config.model()), AnthropicLLMClient.DEFAULT_MAX_TOKENS,
                AnthropicLLMClient.defaultTransport());
        if (options.requireLlm() && !llm.isAvailable()) {
            System.err.println("-LLMAPI: no API key configured. Put LLM_API_KEY=<your key> in .env in the "
                    + "working directory, or set the LLM_API_KEY environment variable.");
            System.exit(3);
            return;
        }

        RealEstateService service = RealEstateService.create(new PreferenceStore(), llm);
        File data = options.data().isPresent() ? options.data().get() : bundledListings();
        LoadSummary summary;
        try {
            summary = service.loadListings(data);
        } catch (UnsupportedFormatException | IOException e) {
            System.err.println("Could not load listings: " + e.getMessage());
            System.exit(2);
            return;
        }
        List<String> warnings = service.restoreSession();

        if (!options.cli()) {
            MainWindow.launch(service, summary, warnings, llm.isAvailable());
            return;
        }

        OutputStream transcript = options.output().isPresent() ? new FileOutputStream(options.output().get()) : null;
        PrintStream out = transcript == null ? System.out
                : new PrintStream(new TeeOutputStream(System.out, transcript), true, StandardCharsets.UTF_8);
        int exit = 0;
        try {
            out.println(summary.describe());
            summary.rejections().forEach(r -> out.println("  rejected " + r));
            warnings.forEach(w -> out.println("Warning: " + w));
            if (!llm.isAvailable()) {
                out.println("Note: no API key configured; the AI advisor is unavailable but every other command works.");
            } else if (options.input().isPresent()) {
                out.println("LLM API: Anthropic, model " + llm.getModel());
            }
            exit = runCli(service, options, out);
            if (options.input().isPresent() && llm.isAvailable()) {
                out.println();
                out.println("Tokens used: " + llm.getInputTokens() + " input, " + llm.getOutputTokens() + " output");
            }
        } finally {
            out.flush();
            if (transcript != null) {
                transcript.close();
            }
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }

    private static int runCli(RealEstateService service, LaunchOptions options, PrintStream out) throws IOException {
        BufferedReader none = new BufferedReader(new StringReader(""));
        if (options.input().isPresent()) {
            List<String> turns = LaunchOptions.taskTurns(
                    Files.readAllLines(options.input().get().toPath(), StandardCharsets.UTF_8));
            if (turns.isEmpty()) {
                System.err.println("Task file is empty: " + options.input().get());
                return 2;
            }
            return new CLIRenderer(service, none, out).runTask(turns) ? 0 : 1;
        }
        if (!options.commands().isEmpty()) {
            new CLIRenderer(service, none, out).runCommands(options.commands());
        } else if (options.script().isPresent()) {
            try (BufferedReader file = Files.newBufferedReader(options.script().get().toPath(),
                    StandardCharsets.UTF_8)) {
                new CLIRenderer(service, file, out).run(true);
            }
        } else {
            new CLIRenderer(service, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                    out).run();
        }
        return 0;
    }

    /** Service wired with the agent; without an API key the agent reports itself unavailable. */
    public static RealEstateService createService() {
        LLMClient llm = AnthropicLLMClient.fromConfig(EnvConfig.load());
        return RealEstateService.create(new PreferenceStore(), llm);
    }

    /** The bundled listings.json as a file, copied out of the jar if necessary. */
    static File bundledListings() throws IOException {
        URL url = Main.class.getResource("/data/listings.json");
        if (url == null) {
            throw new IOException("Bundled listings.json not found on the classpath");
        }
        if ("file".equals(url.getProtocol())) {
            try {
                return new File(url.toURI());
            } catch (URISyntaxException e) {
                throw new IOException(e);
            }
        }
        Path dir = Files.createTempDirectory("real-estate-agent");
        Path temp = dir.resolve("listings.json");
        dir.toFile().deleteOnExit();
        temp.toFile().deleteOnExit();
        try (InputStream in = url.openStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.toFile();
    }
}
