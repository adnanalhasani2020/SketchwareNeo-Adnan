package neo.sketchware.plugin.marketplace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Version helpers for app/plugin versions such as v7.0.0 and 1.2.0-beta. */
public final class PluginVersionUtils {
    private PluginVersionUtils() {}

    private static final String IDENTIFIER = "[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*";
    private static final java.util.regex.Pattern VERSION_PATTERN = java.util.regex.Pattern.compile(
            "(?i)v?[0-9]+(?:\\.[0-9]+){0,5}(?:-" + IDENTIFIER + ")?(?:\\+" + IDENTIFIER + ")?");

    public static boolean isValidVersion(String version) {
        return version != null && VERSION_PATTERN.matcher(version.trim()).matches();
    }

    /**
     * Compares numeric release components and prerelease identifiers (SemVer-like ordering).
     * Build metadata after '+' does not affect precedence.
     */
    public static int compareVersions(String first, String second) {
        ParsedVersion a = parse(first);
        ParsedVersion b = parse(second);
        int coreLength = Math.max(a.core.size(), b.core.size());
        for (int i = 0; i < coreLength; i++) {
            BigInteger x = i < a.core.size() ? a.core.get(i) : BigInteger.ZERO;
            BigInteger y = i < b.core.size() ? b.core.get(i) : BigInteger.ZERO;
            int compared = x.compareTo(y);
            if (compared != 0) return compared;
        }

        if (a.prerelease.isEmpty() && b.prerelease.isEmpty()) return 0;
        if (a.prerelease.isEmpty()) return 1; // A release is newer than its prerelease.
        if (b.prerelease.isEmpty()) return -1;

        int prereleaseLength = Math.min(a.prerelease.size(), b.prerelease.size());
        for (int i = 0; i < prereleaseLength; i++) {
            String x = a.prerelease.get(i);
            String y = b.prerelease.get(i);
            boolean xNumeric = x.matches("[0-9]+");
            boolean yNumeric = y.matches("[0-9]+");
            int compared;
            if (xNumeric && yNumeric) {
                compared = new BigInteger(x).compareTo(new BigInteger(y));
            } else if (xNumeric != yNumeric) {
                compared = xNumeric ? -1 : 1;
            } else {
                compared = x.compareToIgnoreCase(y);
            }
            if (compared != 0) return compared;
        }
        return Integer.compare(a.prerelease.size(), b.prerelease.size());
    }

    private static ParsedVersion parse(String version) {
        if (version == null) return new ParsedVersion(List.of(BigInteger.ZERO), List.of());
        String clean = version.trim().toLowerCase(Locale.ROOT);
        if (clean.startsWith("v")) clean = clean.substring(1);
        int build = clean.indexOf('+');
        if (build >= 0) clean = clean.substring(0, build);
        int dash = clean.indexOf('-');
        String prerelease = dash >= 0 ? clean.substring(dash + 1) : "";
        String core = dash >= 0 ? clean.substring(0, dash) : clean;

        List<BigInteger> coreParts = new ArrayList<>();
        for (String part : core.split("\\.")) {
            try {
                coreParts.add(part.isEmpty() ? BigInteger.ZERO : new BigInteger(part));
            } catch (NumberFormatException ignored) {
                coreParts.add(BigInteger.ZERO);
            }
        }
        if (coreParts.isEmpty()) coreParts.add(BigInteger.ZERO);
        List<String> preParts = new ArrayList<>();
        if (!prerelease.isEmpty()) {
            for (String part : prerelease.split("\\.")) if (!part.isEmpty()) preParts.add(part);
        }
        return new ParsedVersion(coreParts, preParts);
    }

    private record ParsedVersion(List<BigInteger> core, List<String> prerelease) {}
}
