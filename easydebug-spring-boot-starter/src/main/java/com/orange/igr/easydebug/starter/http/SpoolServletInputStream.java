package com.orange.igr.easydebug.starter.http;

import com.orange.igr.easydebug.Spool;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;
import java.io.InputStream;

final class SpoolServletInputStream extends ServletInputStream {

    private final InputStream in;
    private boolean finished;

    SpoolServletInputStream(Spool spool) throws IOException {
        this.in = spool.open();
    }

    @Override
    public int read() throws IOException {
        int n = in.read();
        if (n < 0) {
            finished = true;
        }
        return n;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        int n = in.read(buffer, off, len);
        if (n < 0) {
            finished = true;
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        finished = true;
        in.close();
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("EasyDebug buffers the servlet body; non-blocking reads are not used");
    }
}
