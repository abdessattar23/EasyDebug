package io.github.abdessattar23.easydebug.starter.http;

import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.Payload;
import io.github.abdessattar23.easydebug.Spool;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Tees the response bytes into a spool while the application writes them to the client. */
final class CapturingResponse extends HttpServletResponseWrapper {

    private final Spool spool;
    private ServletOutputStream stream;
    private PrintWriter writer;
    private int status = 200;

    CapturingResponse(HttpServletResponse response, CapturePolicy policy) {
        super(response);
        this.spool = new Spool(policy.maxBodyBytes(), policy.spoolMemoryBytes());
    }

    @Override
    public void setStatus(int status) {
        this.status = status;
        super.setStatus(status);
    }

    @Override
    public void sendError(int status) throws IOException {
        this.status = status;
        super.sendError(status);
    }

    @Override
    public void sendError(int status, String message) throws IOException {
        this.status = status;
        super.sendError(status, message);
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() already called");
        }
        if (stream == null) {
            ServletOutputStream original = getResponse().getOutputStream();
            stream = new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    spool.write(new byte[]{(byte) b});
                    original.write(b);
                }

                @Override
                public void write(byte[] buffer, int off, int len) throws IOException {
                    spool.write(buffer, off, len);
                    original.write(buffer, off, len);
                }

                @Override
                public void flush() throws IOException {
                    original.flush();
                }

                @Override
                public boolean isReady() {
                    return original.isReady();
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    original.setWriteListener(writeListener);
                }
            };
        }
        return stream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (stream != null) {
            throw new IllegalStateException("getOutputStream() already called");
        }
        if (writer == null) {
            Charset charset = charset(getCharacterEncoding());
            Writer original = new OutputStreamWriter(getResponse().getOutputStream(), charset);
            writer = new PrintWriter(new Writer() {
                @Override
                public void write(char[] buffer, int off, int len) throws IOException {
                    original.write(buffer, off, len);
                    spool.write(new String(buffer, off, len).getBytes(charset));
                }

                @Override
                public void flush() throws IOException {
                    original.flush();
                }

                @Override
                public void close() throws IOException {
                    original.flush();
                }
            }, false);
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        super.flushBuffer();
    }

    Payload payload() {
        if (writer != null) {
            writer.flush();
        }
        return spool.toPayload(getContentType(), getHeader("Content-Disposition"));
    }

    void closeSpool() {
        spool.close();
    }

    private static Charset charset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }
}
