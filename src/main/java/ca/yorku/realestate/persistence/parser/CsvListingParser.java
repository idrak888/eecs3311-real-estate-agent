package ca.yorku.realestate.persistence.parser;

import ca.yorku.realestate.domain.Listing;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses listings from CSV with a header row.
 *
 * <p>Columns are matched by header name (case-insensitive), so column order does not matter.
 * Fields may be double-quoted to contain commas; a doubled quote inside a quoted field is a
 * literal quote. Multiple features go in one field separated by semicolons. Blank lines are
 * ignored.
 */
public class CsvListingParser implements ListingParser {

    private static final List<String> KNOWN_FIELDS = List.of(
            ListingRecordMapper.ID, ListingRecordMapper.PRICE, ListingRecordMapper.BEDROOMS,
            ListingRecordMapper.BATHROOMS, ListingRecordMapper.SQUARE_FEET,
            ListingRecordMapper.TYPE, ListingRecordMapper.NEIGHBORHOOD,
            ListingRecordMapper.YEAR_BUILT, ListingRecordMapper.FEATURES);

    private final List<String> rejections = new ArrayList<>();

    @Override
    public List<Listing> parse(File file) throws IOException {
        rejections.clear();
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        int headerIndex = firstNonBlank(lines);
        if (headerIndex < 0) {
            throw new IOException(file.getName() + " is empty; expected a header row");
        }
        List<String> header = canonicalHeader(splitLine(stripBom(lines.get(headerIndex))));

        List<Listing> listings = new ArrayList<>();
        int recordNumber = 0;
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            recordNumber++;
            List<String> values;
            try {
                values = splitLine(line);
            } catch (IllegalArgumentException e) {
                rejections.add(ListingRecordMapper.rejection(recordNumber, null, e.getMessage()));
                continue;
            }
            if (values.size() != header.size()) {
                rejections.add(ListingRecordMapper.rejection(recordNumber, null,
                        "expected " + header.size() + " columns but found " + values.size()));
                continue;
            }
            Map<String, String> record = new HashMap<>();
            for (int c = 0; c < header.size(); c++) {
                record.put(header.get(c), values.get(c));
            }
            try {
                listings.add(ListingRecordMapper.toListing(record));
            } catch (IllegalArgumentException e) {
                rejections.add(ListingRecordMapper.rejection(recordNumber, record, e.getMessage()));
            }
        }
        return listings;
    }

    private static int firstNonBlank(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Maps header names onto the canonical field names, ignoring case and spaces. */
    private static List<String> canonicalHeader(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String h : raw) {
            String key = h.trim();
            String match = key;
            for (String known : KNOWN_FIELDS) {
                if (known.equalsIgnoreCase(key.replace(" ", "").replace("_", ""))) {
                    match = known;
                    break;
                }
            }
            out.add(match);
        }
        return out;
    }

    /**
     * Splits one CSV line into fields, honouring double quotes.
     *
     * @throws IllegalArgumentException if a quoted field is never closed
     */
    static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("unterminated quoted field");
        }
        fields.add(current.toString());
        return fields;
    }

    @Override
    public boolean supports(String extension) {
        return "csv".equalsIgnoreCase(extension);
    }

    @Override
    public List<String> getRejections() {
        return List.copyOf(rejections);
    }
}
