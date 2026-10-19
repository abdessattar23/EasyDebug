package com.orange.igr.easydebug;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Full-body spool. Bytes stay in memory until {@code memoryMax}, then a temp file.
 * The prefix used for the stored body is capped separately. The hash covers every byte.
 */
public final class Spool implements Closeable {

    private final int prefixMax;
    private final int memoryMax;
    private final MessageDigest digest;
    private final ByteArrayOutputStream prefix = new ByteArrayOutputStream();
    private ByteArrayOutputStream memory = new ByteArrayOutputStream();
    private File file;
    private OutputStream fileOut;
    private long size;
    private String sha256;

    public Spool(int prefixMax, int memoryMax) {
        this.prefixMax = Math.max(0, prefixMax);
        this.memoryMax = Math.max(0, memoryMax);
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Spool capture(InputStream in, int prefixMax, int memoryMax) throws IOException {
        Spool spool = new Spool(prefixMax, memoryMax);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            spool.write(buffer, 0, read);
        }
        return spool;
    }

    public void write(byte[] bytes) throws IOException {
        if (bytes != null && bytes.length > 0) {
            write(bytes, 0, bytes.length);
        }
    }

    public void write(byte[] bytes, int off, int len) throws IOException {
        if (len <= 0) {
            return;
        }
        if (sha256 != null) {
            throw new IllegalStateException("spool is already finalized");
        }
        digest.update(bytes, off, len);
        int room = prefixMax - prefix.size();
        if (room > 0) {
            prefix.write(bytes, off, Math.min(room, len));
        }
        if (fileOut == null && memory.size() + len <= memoryMax) {
            memory.write(bytes, off, len);
        } else {
            spill();
            fileOut.write(bytes, off, len);
        }
        size += len;
    }

    public long size() {
        return size;
    }

    public Payload toPayload(String contentType, String contentDisposition) {
        return BodyCapture.payload(prefix.toByteArray(), size, sha256(), contentType, contentDisposition, prefixMax);
    }

    public InputStream open() throws IOException {
        if (fileOut != null) {
            fileOut.flush();
            return new FileInputStream(file);
        }
        return new ByteArrayInputStream(memory.toByteArray());
    }

    public String sha256() {
        if (sha256 == null) {
            sha256 = HexFormat.of().formatHex(digest.digest());
        }
        return sha256;
    }

    @Override
    public void close() {
        try {
            if (fileOut != null) {
                fileOut.close();
            }
        } catch (IOException ignored) {
            // the request is already finished
        }
        if (file != null) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException ignored) {
                file.deleteOnExit();
            }
        }
    }

    private void spill() throws IOException {
        if (fileOut != null) {
            return;
        }
        file = Files.createTempFile("easydebug-", ".body").toFile();
        fileOut = new FileOutputStream(file);
        fileOut.write(memory.toByteArray());
        memory = new ByteArrayOutputStream(0);
    }
}
