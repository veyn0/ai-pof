package dev.veyno.aiPof.domain;

import java.util.Locale;

public record RoundId(String value) {
    public static RoundId fromRaw(String raw) {
        String normalized = normalize(raw);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("ID darf nicht leer sein.");
        }
        return new RoundId(normalized);
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public String baseId() {
        int dashIndex = value.lastIndexOf('-');
        if (dashIndex <= 0) {
            return value;
        }
        String suffix = value.substring(dashIndex + 1);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return value;
            }
        }
        return value.substring(0, dashIndex);
    }

    public int counter() {
        String base = baseId();
        if (value.equals(base)) {
            return 1;
        }
        if (value.length() == base.length() || value.charAt(base.length()) != '-') {
            return 1;
        }
        String suffix = value.substring(base.length() + 1);
        if (suffix.isEmpty()) {
            return 1;
        }
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return 1;
            }
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public RoundId next(int nextCounter) {
        return new RoundId(baseId() + "-" + nextCounter);
    }
}
