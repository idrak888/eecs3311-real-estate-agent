package ca.yorku.realestate.app;

import ca.yorku.realestate.agent.llm.AnthropicLLMClient;
import ca.yorku.realestate.agent.llm.LLMClient;
import ca.yorku.realestate.config.EnvConfig;
import ca.yorku.realestate.persistence.LoadSummary;
import ca.yorku.realestate.persistence.PreferenceStore;
import ca.yorku.realestate.service.RealEstateService;
import ca.yorku.realestate.ui.cli.CLIRenderer;
import ca.yorku.realestate.ui.gui.MainWindow;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Application entry point. Starts the GUI by default, or the CLI with {@code --cli}. Both use the
 * same {@link RealEstateService}, wired with the agent and the API key from {@code .env}.
 *
 * <p>Options are parsed by {@link LaunchOptions}: {@code --cli} reads commands from standard input
 * (keyboard, pipe or redirect), {@code --script <file>} runs a command file, {@code --command
 * "<cmd>"} runs one-shot commands, and {@code --data <file>} loads a different listing file.
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
        if (options.script().isPresent() && !options.script().get().isFile()) {
            System.err.println("Command file not found: " + options.script().get());
            System.exit(2);
            return;
        }
        File data = options.data().orElse(null);
        if (data == null) {
            data = bundledListings();
        }

        RealEstateService service = createService();
        LoadSummary summary = service.loadListings(data);
        List<String> warnings = service.restoreSession();

        if (options.cli()) {
            System.out.println(summary.describe());
            summary.rejections().forEach(r -> System.out.println("  rejected " + r));
            warnings.forEach(w -> System.out.println("Warning: " + w));
            if (!service.hasAgent() || !aiConfigured()) {
                System.out.println("Note: no API key in .env; 'ask' is unavailable but every other command works.");
            }
            runCli(service, options);
        } else {
            MainWindow.launch(service, summary, warnings, aiConfigured());
        }
    }

    private static void runCli(RealEstateService service, LaunchOptions options) throws IOException {
        if (!options.commands().isEmpty()) {
            new CLIRenderer(service, new BufferedReader(new StringReader("")), System.out)
                    .runCommands(options.commands());
        } else if (options.script().isPresent()) {
            try (BufferedReader file = Files.newBufferedReader(options.script().get().toPath(),
                    StandardCharsets.UTF_8)) {
                new CLIRenderer(service, file, System.out).run(true);
            }
        } else {
            new CLIRenderer(service, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                    System.out).run();
        }
    }

    /** Service wired with the agent; without an API key the agent reports itself unavailable. */
    public static RealEstateService createService() {
        LLMClient llm = AnthropicLLMClient.fromConfig(EnvConfig.load());
        return RealEstateService.create(new PreferenceStore(), llm);
    }

    static boolean aiConfigured() {
        return EnvConfig.load().apiKey().isPresent();
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
        Path temp = Files.createTempFile("listings", ".json");
        temp.toFile().deleteOnExit();
        try (InputStream in = url.openStream()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.toFile();
    }
}
