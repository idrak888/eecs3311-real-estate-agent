package ca.yorku.realestate.persistence.parser;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/** Locates files under src/test/resources/fixtures. */
final class Fixtures {

    private Fixtures() {
    }

    static File file(String name) {
        URL url = Fixtures.class.getResource("/fixtures/" + name);
        if (url == null) {
            throw new IllegalStateException("Missing test fixture: " + name);
        }
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
