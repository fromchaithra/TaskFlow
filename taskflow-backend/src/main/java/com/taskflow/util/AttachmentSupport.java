package com.taskflow.util;

import com.taskflow.exception.BusinessRuleException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Filename and content-type hardening shared by bug-attachment upload/download. */
public final class AttachmentSupport {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp",
            "application/pdf", "text/plain", "text/csv",
            "application/zip", "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private static final Set<String> ZIP_FAMILY = Set.of(
            "application/zip", "application/x-zip-compressed",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private AttachmentSupport() {
    }

    public static boolean isAllowedType(String contentType) {
        return contentType != null && ALLOWED_TYPES.contains(contentType);
    }

    /**
     * Removes path components, control characters and quotes so the result is safe in a
     * quoted Content-Disposition header. Unicode letters/digits are preserved.
     */
    public static String sanitizeFilename(String original) {
        if (original == null) return "file";
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);

        StringBuilder result = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c < 0x20 || c == 0x7f || c == '"' || c == ';') continue;
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_'
                    || c == ' ' || c == '(' || c == ')') {
                result.append(c);
            } else {
                result.append('_');
            }
        }
        String sanitized = result.toString().trim();
        while (sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200).trim();
        }
        return sanitized.isEmpty() ? "file" : sanitized;
    }

    /** Verifies the declared MIME type against the file's magic bytes / text shape. */
    public static String resolveContentType(byte[] bytes, String declared) {
        if (!isAllowedType(declared)) {
            throw new BusinessRuleException("File type not allowed: " + declared);
        }
        String detected = detect(bytes);
        boolean valid;
        if (declared.startsWith("image/") || declared.equals("application/pdf")) {
            valid = declared.equals(detected);
        } else if (ZIP_FAMILY.contains(declared)) {
            valid = "zip".equals(detected);
        } else if (declared.equals("application/msword")) {
            valid = "ole2".equals(detected);
        } else {
            valid = "text".equals(detected);
        }
        if (!valid) {
            throw new BusinessRuleException("File content does not match the declared type.");
        }
        return declared;
    }

    private static String detect(byte[] b) {
        if (startsWith(b, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "image/png";
        if (startsWith(b, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) return "image/jpeg";
        if (startsWith(b, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(b, "GIF89a".getBytes(StandardCharsets.US_ASCII))) return "image/gif";
        if (startsWith(b, "RIFF".getBytes(StandardCharsets.US_ASCII)) && has(b, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return "image/webp";
        }
        if (startsWith(b, "%PDF".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (startsWith(b, new byte[]{'P', 'K', 0x03, 0x04})
                || startsWith(b, new byte[]{'P', 'K', 0x05, 0x06})) return "zip";
        if (startsWith(b, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0})) return "ole2";
        return isText(b) ? "text" : "unknown";
    }

    private static boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) return false;
        }
        return true;
    }

    private static boolean has(byte[] bytes, int offset, byte[] signature) {
        if (bytes.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[offset + i] != signature[i]) return false;
        }
        return true;
    }

    private static boolean isText(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }
}
