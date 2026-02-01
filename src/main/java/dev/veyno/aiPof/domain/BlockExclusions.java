package dev.veyno.aiPof.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class BlockExclusions {
    private final List<String> patterns;

    public BlockExclusions(List<String> patterns) {
        this.patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }

    /**
     * Matches a block name against configured exclusions.
     * <ul>
     *   <li>Case-insensitive comparison.</li>
     *   <li>Entries containing '*' are treated as contains matches.</li>
     *   <li>Entries without '*' must match exactly.</li>
     * </ul>
     */
    public boolean matches(String blockName) {
        if (blockName == null || patterns.isEmpty()) {
            return false;
        }
        String normalizedName = blockName.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            String normalizedPattern = pattern.trim().toLowerCase(Locale.ROOT);
            if (normalizedPattern.isEmpty()) {
                continue;
            }
            boolean containsMatch = normalizedPattern.contains("*");
            String token = normalizedPattern.replace("*", "");
            if (token.isEmpty()) {
                continue;
            }
            if (containsMatch) {
                if (normalizedName.contains(token)) {
                    return true;
                }
            } else if (Objects.equals(normalizedName, token)) {
                return true;
            }
        }
        return false;
    }
}
