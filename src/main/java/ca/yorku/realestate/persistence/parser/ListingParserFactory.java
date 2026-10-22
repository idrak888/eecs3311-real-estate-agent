package ca.yorku.realestate.persistence.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Creator in the Factory Method pattern: chooses a {@link ListingParser} from the file's
 * extension at load time.
 *
 * <p>Callers ask for a parser and call {@code parse}; they never name a concrete parser class.
 * Each call returns a fresh parser, since parsers hold the rejection list of their last parse.
 * Supporting a new format means adding one parser class and one {@link #register} call.
 */
public class ListingParserFactory {

    private final Map<String, Supplier<ListingParser>> parsers = new LinkedHashMap<>();

    public ListingParserFactory() {
        register("json", JsonListingParser::new);
        register("csv", CsvListingParser::new);
    }

    /** Registers a parser for an extension (without the dot). */
    public final void register(String extension, Supplier<ListingParser> supplier) {
        parsers.put(extension.toLowerCase(Locale.ROOT), supplier);
    }

    /**
     * @throws UnsupportedFormatException if no parser supports the file's extension
     */
    public ListingParser createParser(File file) {
        String extension = extensionOf(file.getName());
        Supplier<ListingParser> supplier = parsers.get(extension);
        if (supplier != null) {
            ListingParser parser = supplier.get();
            if (parser.supports(extension)) {
                return parser;
            }
        }
        throw new UnsupportedFormatException(file.getName(), supportedFormats());
    }

    /** Extensions this factory can parse, e.g. {@code [.json, .csv]}. */
    public List<String> supportedFormats() {
        List<String> formats = new ArrayList<>();
        for (String ext : parsers.keySet()) {
            formats.add("." + ext);
        }
        return formats;
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
