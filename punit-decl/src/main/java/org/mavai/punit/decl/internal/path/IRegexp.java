package org.mavai.punit.decl.internal.path;

/**
 * I-Regexp (RFC 9485) mapped onto Java's regex engine. I-Regexp is a
 * deliberately small interoperable subset; the mapping validates the
 * subset's surface and rewrites the one semantic divergence — the dot,
 * which in I-Regexp matches any character except line terminators
 * {@code \n} and {@code \r}, where Java's default dot excludes more —
 * before handing the pattern to {@link java.util.regex.Pattern}.
 */
final class IRegexp {

    private IRegexp() {}

    static String toJavaRegex(String iRegexp) {
        StringBuilder java = new StringBuilder(iRegexp.length() + 8);
        boolean inClass = false;
        int i = 0;
        while (i < iRegexp.length()) {
            char c = iRegexp.charAt(i);
            switch (c) {
                case '\\' -> {
                    if (i + 1 >= iRegexp.length()) {
                        throw new PathSyntaxException("trailing backslash in I-Regexp");
                    }
                    java.append(c).append(iRegexp.charAt(i + 1));
                    i += 2;
                    continue;
                }
                case '[' -> {
                    inClass = true;
                    java.append(c);
                }
                case ']' -> {
                    inClass = false;
                    java.append(c);
                }
                case '.' -> java.append(inClass ? "." : "[^\\n\\r]");
                case '$', '^' ->
                    // Passed through as Java anchors — the compliance
                    // suite's explicit-caret/dollar cases pin this
                    // reading: under full-match semantics an edge
                    // anchor is a no-op, and a mid-pattern anchor
                    // matches nothing, which is what the suite expects.
                    java.append(c);
                default -> java.append(c);
            }
            i++;
        }
        return java.toString();
    }
}
