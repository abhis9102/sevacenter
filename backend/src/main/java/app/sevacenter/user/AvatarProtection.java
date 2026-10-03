package app.sevacenter.user;

import app.sevacenter.web.InvalidFieldException;

/**
 * Validates uploaded user avatars:
 * <ul>
 *   <li>Enforces 2 MB max size;</li>
 *   <li>Inspects magic bytes directly (PNG, JPEG, WebP only) to prevent MIME spoofing;</li>
 *   <li>Strictly rejects SVG and HTML to eliminate Stored XSS vectors (threat model invariant).</li>
 * </ul>
 */
public final class AvatarProtection {

    public static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    private AvatarProtection() { }

    public static String detectContentType(byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidFieldException("avatar", "Avatar file is empty");
        }
        if (data.length > MAX_AVATAR_BYTES) {
            throw new InvalidFieldException("avatar", "Avatar image must be 2 MB or less");
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 0x50
                && data[2] == 0x4E
                && data[3] == 0x47
                && data[4] == 0x0D
                && data[5] == 0x0A
                && data[6] == 0x1A
                && data[7] == 0x0A) {
            return "image/png";
        }

        // JPEG: FF D8 FF
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        // WebP: RIFF ... WEBP
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }

        throw new InvalidFieldException("avatar", "Only PNG, JPEG, and WebP images are allowed");
    }
}
