package ca.yorku.realestate.persistence.parser;

import ca.yorku.realestate.domain.Listing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses listings from JSON.
 *
 * <p>Accepted shapes: a top-level array of listing objects, or an object with a
 * {@code "listings"} array. Features may be a JSON array of strings or a single
 * semicolon-separated string.
 */
public class JsonListingParser implements ListingParser {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> rejections = new ArrayList<>();

    @Override
    public List<Listing> parse(File file) throws IOException {
        rejections.clear();
        JsonNode root;
        try {
            root = mapper.readTree(file);
        } catch (JsonProcessingException e) {
            throw new IOException("Invalid JSON in " + file.getName() + ": "
                    + e.getOriginalMessage(), e);
        }

        JsonNode records = root;
        if (root != null && root.isObject() && root.has("listings")) {
            records = root.get("listings");
        }
        if (records == null || !records.isArray()) {
            throw new IOException(file.getName()
                    + " must contain a JSON array of listings or an object with a \"listings\" array");
        }

        List<Listing> listings = new ArrayList<>();
        int recordNumber = 0;
        for (JsonNode node : records) {
            recordNumber++;
            if (!node.isObject()) {
                rejections.add(ListingRecordMapper.rejection(recordNumber, null,
                        "expected a JSON object"));
                continue;
            }
            Map<String, String> record = toRecord(node);
            try {
                listings.add(ListingRecordMapper.toListing(record));
            } catch (IllegalArgumentException e) {
                rejections.add(ListingRecordMapper.rejection(recordNumber, record, e.getMessage()));
            }
        }
        return listings;
    }

    private static Map<String, String> toRecord(JsonNode node) {
        Map<String, String> record = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isArray()) {
                List<String> parts = new ArrayList<>();
                for (JsonNode item : value) {
                    if (!item.isNull()) {
                        parts.add(item.asText());
                    }
                }
                record.put(field.getKey(), String.join(ListingRecordMapper.FEATURE_SEPARATOR, parts));
            } else {
                record.put(field.getKey(), value.asText());
            }
        }
        return record;
    }

    @Override
    public boolean supports(String extension) {
        return "json".equalsIgnoreCase(extension);
    }

    @Override
    public List<String> getRejections() {
        return List.copyOf(rejections);
    }
}
