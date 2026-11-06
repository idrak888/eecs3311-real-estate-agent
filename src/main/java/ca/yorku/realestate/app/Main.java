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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

/**
 * Application entry point. Starts the GUI by default, or the CLI with {@code --cli}. Both use the
 * same {@link RealEstateService}, wired with the agent and the API key from {@code .env}.
 *
 * <p>Options: {@code --cli}; {@code --data <file>} to load a different listing file.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        List<String> a = Arrays.asList(args);
        boolean cli = a.contains("--cli");
        int dataIndex = a.indexOf("--data");
        File data = dataIndex >= 0 && dataIndex + 1 < a.size() ? new File(a.get(dataIndex + 1)) : bundledListings();

        RealEstateService service = createService();
        LoadSummary summary = service.loadListings(data);
        List<String> warnings = service.restoreSession();

        if (cli) {
            System.out.println(summary.describe());
            summary.rejections().forEach(r -> System.out.println("  rejected " + r));
            warnings.forEach(w -> System.out.println("Warning: " + w));
            if (!service.hasAgent() || !aiConfigured()) {
                System.out.println("Note: no API key in .env; 'ask' is unavailable but every other command works.");
            }
            new CLIRenderer(service, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                    System.out).run();
        } else {
            MainWindow.launch(service, summary, warnings, aiConfigured());
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
