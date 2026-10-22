package ca.yorku.realestate.persistence.parser;

import java.util.List;

/** Thrown when no registered parser supports a file's extension. */
public class UnsupportedFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> supportedFormats;

    public UnsupportedFormatException(String fileName, List<String> supportedFormats) {
        super("Unsupported listing file format: '" + fileName + "'. Supported formats: "
                + String.join(", ", supportedFormats));
        this.supportedFormats = List.copyOf(supportedFormats);
    }

    public List<String> getSupportedFormats() {
        return supportedFormats;
    }
}
