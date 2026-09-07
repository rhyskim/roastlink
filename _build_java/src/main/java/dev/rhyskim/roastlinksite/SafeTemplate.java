package dev.rhyskim.roastlinksite;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal re-implementation of Python's {@code string.Template.safe_substitute}:
 * replaces {@code $name} / {@code ${name}} with a value from the given map,
 * leaves the placeholder text untouched when the name isn't in the map
 * (instead of throwing, like Python's plain {@code substitute} would), and
 * treats {@code $$} as an escaped literal {@code $}.
 *
 * <p>The site's content fragments only ever reference a handful of known
 * placeholders (mainly {@code $base_path}), so "unknown placeholder stays
 * literal" matters -- it's what lets a fragment be templated once for
 * {@code $base_path} and then embedded, as-is, into a second substitution
 * pass over the outer page template without double-processing.
 */
final class SafeTemplate {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$(\\$|[_a-zA-Z][_a-zA-Z0-9]*|\\{[_a-zA-Z][_a-zA-Z0-9]*\\})");

    private final String source;

    SafeTemplate(String source) {
        this.source = source;
    }

    String safeSubstitute(Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(source);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            String replacement;
            if (token.equals("$")) {
                replacement = "$";
            } else {
                String name = token.startsWith("{") ? token.substring(1, token.length() - 1) : token;
                replacement = values.containsKey(name) ? values.get(name) : m.group(0);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
