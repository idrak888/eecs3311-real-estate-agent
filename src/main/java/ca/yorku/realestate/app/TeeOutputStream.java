package ca.yorku.realestate.app;

import java.io.IOException;
import java.io.OutputStream;

/** Writes everything to two streams; used by {@code -output} to save the CLI transcript. */
final class TeeOutputStream extends OutputStream {

    private final OutputStream first;
    private final OutputStream second;

    TeeOutputStream(OutputStream first, OutputStream second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public void write(int b) throws IOException {
        first.write(b);
        second.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        first.write(b, off, len);
        second.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        first.flush();
        second.flush();
    }
}
