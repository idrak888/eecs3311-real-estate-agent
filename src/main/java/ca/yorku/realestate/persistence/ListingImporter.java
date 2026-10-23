package ca.yorku.realestate.persistence;

import ca.yorku.realestate.domain.Listing;
import ca.yorku.realestate.persistence.parser.ListingParser;
import ca.yorku.realestate.persistence.parser.ListingParserFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the F01 import: factory-selected parser, then repository load.
 *
 * <p>The importer depends only on the {@link ListingParser} interface; which concrete parser
 * runs is decided by {@link ListingParserFactory} from the file name.
 */
public class ListingImporter {

    private final ListingParserFactory factory;
    private final ListingRepository repository;

    public ListingImporter(ListingParserFactory factory, ListingRepository repository) {
        this.factory = factory;
        this.repository = repository;
    }

    /**
     * Imports the file into the repository, replacing what was there.
     *
     * @throws FileNotFoundException if the file does not exist, naming the expected path
     * @throws IOException if the file cannot be read or has the wrong overall structure
     * @throws ca.yorku.realestate.persistence.parser.UnsupportedFormatException for an
     *     unsupported extension
     */
    public LoadSummary importFile(File file) throws IOException {
        if (!file.isFile()) {
            throw new FileNotFoundException("Listing file not found at expected path: "
                    + file.getAbsolutePath());
        }
        ListingParser parser = factory.createParser(file);
        List<Listing> parsed = parser.parse(file);
        repository.load(parsed);

        List<String> rejections = new ArrayList<>(parser.getRejections());
        for (String id : repository.getDuplicateIds()) {
            rejections.add("duplicate id " + id + ": kept the first occurrence");
        }
        return new LoadSummary(file.getName(), repository.count(), rejections);
    }
}
