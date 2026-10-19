package com.orange.igr.easydebug;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hash of the full bytes, prefix kept up to the cap, filename when the headers or a multipart body carry one. */
public final class BodyCapture {

    private static final Pattern FILENAME = Pattern.compile("filename\\*?\\s*=\\s*(?:UTF-8''|\"?)([^\";\\r\\n]+)",
            Pattern.CASE_INSENSITIVE);

    private BodyCapture() {
    }

    public static Payload capture(byte[] body, String contentType, String contentDisposition, int maxBytes) {
        byte[] bytes = body == null ? new byte[0] : body;
        try {
            return capture(new ByteArrayInputStream(bytes), contentType, contentDisposition, maxBytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Payload capture(InputStream in, String contentType, String contentDisposition, int maxBytes)
            throws IOException {
        int cap = Math.max(0, maxBytes);
        MessageDigest digest = sha256Digest();
        byte[] prefix = new byte[cap == 0 ? 0 : Math.min(cap, 64 * 1024)];
        int prefixLength = 0;
        long size = 0;
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            digest.update(buffer, 0, read);
            size += read;
            if (prefixLength < cap) {
                int take = (int) Math.min(read, cap - prefixLength);
                if (prefixLength + take > prefix.length) {
                    prefix = Arrays.copyOf(prefix, Math.min(cap, Math.max(prefix.length * 2, prefixLength + take)));
                }
                System.arraycopy(buffer, 0, prefix, prefixLength, take);
                prefixLength += take;
            }
        }
        if (prefixLength != prefix.length) {
            prefix = Arrays.copyOf(prefix, prefixLength);
        }
        return payload(prefix, size, HexFormat.of().formatHex(digest.digest()), contentType, contentDisposition, cap);
    }

    public static Payload payload(byte[] prefix, long size, String sha256, String contentType,
                                  String contentDisposition, int maxBytes) {
        boolean truncated = size > prefix.length;
        String filename = filename(contentDisposition, prefix, contentType);
        if (size == 0) {
            return new Payload(java.util.Map.of(), contentType, filename, 0, sha256, false, null, null);
        }
        if (textual(contentType, prefix)) {
            return new Payload(java.util.Map.of(), contentType, filename, size, sha256, truncated,
                    new String(prefix, StandardCharsets.UTF_8), "text");
        }
        return new Payload(java.util.Map.of(), contentType, filename, size, sha256, truncated,
                java.util.Base64.getEncoder().encodeToString(prefix), "base64");
    }

    public static String sha256(byte[] bytes) {
        byte[] data = bytes == null ? new byte[0] : bytes;
        return HexFormat.of().formatHex(sha256Digest().digest(data));
    }

    public static String filename(String contentDisposition, byte[] prefix, String contentType) {
        String fromHeader = filenameIn(contentDisposition);
        if (fromHeader != null) {
            return fromHeader;
        }
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("multipart") && prefix != null) {
            return filenameIn(new String(prefix, StandardCharsets.ISO_8859_1));
        }
        return null;
    }

    private static String filenameIn(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = FILENAME.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String name = matcher.group(1).trim();
        if (name.endsWith("\"")) {
            name = name.substring(0, name.length() - 1);
        }
        return name.isBlank() ? null : name;
    }

    static boolean textual(String contentType, byte[] prefix) {
        if (contentType == null || contentType.isBlank()) {
            return utf8(prefix);
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        int semi = type.indexOf(';');
        if (semi >= 0) {
            type = type.substring(0, semi).trim();
        }
        if (type.startsWith("text/")) {
            return true;
        }
        if (type.equals("application/json")
                || type.endsWith("+json")
                || type.equals("application/xml")
                || type.endsWith("+xml")
                || type.equals("application/javascript")
                || type.equals("application/x-www-form-urlencoded")
                || type.equals("application/graphql")
                || type.equals("application/x-ndjson")) {
            return true;
        }
        if (type.contains("multipart")) {
            return false;
        }
        return false;
    }

    private static boolean utf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return true;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return Arrays.equals(bytes, text.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
