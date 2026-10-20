package io.github.abdessattar23.easydebug.starter.http;

import io.github.abdessattar23.easydebug.BodyCapture;
import io.github.abdessattar23.easydebug.CapturePolicy;
import io.github.abdessattar23.easydebug.Payload;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies multipart parts when the application first parses them, before the container deletes
 * its temp files at the end of the request.
 */
final class MultipartCaptureRequest extends HttpServletRequestWrapper {

    private final List<byte[]> bodies = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();
    private boolean captured;
    private String captureError;

    MultipartCaptureRequest(HttpServletRequest request) {
        super(request);
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        Collection<Part> current = super.getParts();
        copy(current);
        return current;
    }

    @Override
    public Part getPart(String name) throws IOException, ServletException {
        Part part = super.getPart(name);
        if (part != null) {
            copy(List.of(part));
        }
        return part;
    }

    Payload payload(CapturePolicy policy, HttpServletRequest request, Map<String, Object> attributes) {
        attributes.put("multipart", true);
        attributes.put("bodyIs", "parts");
        if (captureError != null) {
            attributes.put("requestCaptureError", captureError);
        }
        if (!captured) {
            attributes.put("requestCaptureError", "multipart body was not parsed through getParts()");
            long length = request.getContentLengthLong();
            return new Payload(Map.of(), request.getContentType(), null, Math.max(0, length), null, false, null, null);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            boolean truncated = false;
            String filename = null;
            StringBuilder text = new StringBuilder();
            List<Map<String, Object>> described = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                byte[] bytes = bodies.get(i);
                digest.update(bytes);
                size += bytes.length;
                Payload one = BodyCapture.capture(bytes, part.getContentType(), disposition(part), policy.maxBodyBytes());
                truncated = truncated || one.truncated();
                if (filename == null && part.getSubmittedFileName() != null && !part.getSubmittedFileName().isBlank()) {
                    filename = part.getSubmittedFileName();
                }
                text.append("name=").append(part.getName());
                if (part.getSubmittedFileName() != null) {
                    text.append(" filename=").append(part.getSubmittedFileName());
                }
                text.append(" size=").append(bytes.length).append('\n');
                if (one.body() != null) {
                    text.append(one.body()).append('\n');
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", part.getName());
                item.put("filename", part.getSubmittedFileName());
                item.put("contentType", part.getContentType());
                item.put("size", (long) bytes.length);
                item.put("sha256", one.sha256());
                item.put("truncated", one.truncated());
                described.add(item);
            }
            attributes.put("parts", described);
            return new Payload(Map.of(), request.getContentType(), filename, size,
                    HexFormat.of().formatHex(digest.digest()), truncated, text.toString(), "text");
        } catch (Exception e) {
            attributes.put("requestCaptureError", e.toString());
            return new Payload(Map.of(), request.getContentType(), null, 0, null, false, null, null);
        }
    }

    private void copy(Collection<Part> current) {
        if (captured || current == null) {
            return;
        }
        try {
            for (Part part : current) {
                byte[] bytes;
                try (InputStream in = part.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                parts.add(part);
                bodies.add(bytes);
            }
            captured = true;
        } catch (Exception e) {
            captureError = e.toString();
            captured = true;
        }
    }

    private static String disposition(Part part) {
        String header = part.getHeader("Content-Disposition");
        if (header != null) {
            return header;
        }
        if (part.getSubmittedFileName() == null) {
            return null;
        }
        return "form-data; name=\"" + part.getName() + "\"; filename=\"" + part.getSubmittedFileName() + "\"";
    }
}
