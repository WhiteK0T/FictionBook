package org.tehlab.whitek0t.fictionbook.internal.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Обёртка над InputStream, считающая количество прочитанных байт.
 * Используется для определения точного offset'а начала {@code <binary>} в FB2-файле,
 * чтобы потом можно было сделать seek для ленивого чтения base64.
 */
public class ByteCountingInputStream extends FilterInputStream {

    private long bytesRead = 0;
    private long markPosition = 0;

    public ByteCountingInputStream(InputStream in) {
        super(in);
    }

    public long getBytesRead() {
        return bytesRead;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) bytesRead++;
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) bytesRead += n;
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        bytesRead += skipped;
        return skipped;
    }

    @Override
    public synchronized void mark(int readlimit) {
        super.mark(readlimit);
        markPosition = bytesRead;
    }

    @Override
    public synchronized void reset() throws IOException {
        super.reset();
        bytesRead = markPosition;
    }
}