package com.bergerkiller.bukkit.tc.utils;

/**
 * Utilities for escaping and un-escaping String values with quotes.
 * Supports both single and double quotes for unescaping, escapes
 * with only double quotes.
 */
public class QuoteEscapedString {
    private final String unescaped;
    private String escaped;
    private final boolean isQuoteEscaped;

    public QuoteEscapedString(String unescaped) {
        this(unescaped, null, false);
    }

    public QuoteEscapedString(String unescaped, String escaped, boolean isQuoteEscaped) {
        this.unescaped = unescaped;
        this.escaped = escaped;
        this.isQuoteEscaped = isQuoteEscaped;
    }

    /**
     * Gets the unescaped text. This has beginning/ending quotes removed, and escaped
     * characters (\) inside replaced. When parsing a String that is invalidly escaped,
     * this is that original String.
     *
     * @return Original unescaped String
     */
    public String getUnescaped() {
        return unescaped;
    }

    /**
     * Gets the quote-escaped text. This has the beginning/ending quotes added, and
     * special characters are \-escaped.
     *
     * @return Escaped String
     */
    public String getEscaped() {
        if (escaped == null) {
            escaped = escapeString(unescaped);
        }
        return escaped;
    }

    /**
     * Gets whether this quote-escaped String is actually quote-escaped or not.
     * Only use is with {@link #tryParseQuoted(String)}, which passes this property.
     * It is not useful otherwise.
     *
     * @return True if quote-escaped
     */
    public boolean isQuoteEscaped() {
        return isQuoteEscaped;
    }

    @Override
    public String toString() {
        return "QuoteEscapedString{" + unescaped + " QUOTED=" + isQuoteEscaped + "}";
    }

    /**
     * Escapes a String with quotes if it contains characters in it that require that.
     * Examples are spaces and special characters like \.
     *
     * @param str Original unescaped text
     * @return QuoteEscapedString, with {@link #getEscaped()} the result of escaping
     */
    public static QuoteEscapedString quoteEscape(String str) {
        return new QuoteEscapedString(str, escapeString(str), false);
    }

    /**
     * Attempts to parse a potentially quote-escaped String. If the input text
     * is properly encoded, starting and ending with a " or ' character,
     * will decode the original String and return both. If no quoting is found,
     * returns the same input text. Check {@link #isQuoteEscaped()} on the result
     * to see if any un-escaping was done.
     *
     * @param str Input text to parse
     * @return QuoteEscapedString
     */
    public static QuoteEscapedString tryParseQuoted(String str) {
        int len = str.length();
        if (len < 2) {
            return new QuoteEscapedString(str);
        }

        // Verify starts with " or '
        char quoteChar = str.charAt(0);
        if (quoteChar != '"' && quoteChar != '\'') {
            return new QuoteEscapedString(str);
        }

        // Verify ends with the same quote character
        if (str.charAt(len - 1) != quoteChar) {
            return new QuoteEscapedString(str);
        }

        // Optimized path if no special un-escaping is done
        if (str.indexOf('\\', 1) == -1 && str.indexOf(quoteChar, 1) == (len - 1)) {
            return new QuoteEscapedString(str.substring(1, len - 1), str, true);
        }

        StringBuilder newStr = new StringBuilder(len - 1);
        boolean escaped = false;
        int i = 1;
        for (; i < len; i++) {
            char c = str.charAt(i);
            if (escaped) {
                escaped = false;
                newStr.append(c);
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quoteChar) {
                ++i;
                break;
            } else {
                newStr.append(c);
            }
        }

        // Invalid syntax / not entire string is included
        if (escaped || i < len) {
            return new QuoteEscapedString(str);
        }

        return new QuoteEscapedString(newStr.toString(), str, true);
    }

    /**
     * Looks up the next occurrence of a token, taking into account string escaping
     * rules. This way string value like ".." can be specified. Only contents outside
     * of string-escaped sections can be matched.
     *
     * @param text Input text
     * @param token Token to find
     * @param fromIndex Index to start looking from
     * @return Next index where the token is found, where the token is not string-escaped
     */
    public static int unquotedIndexOf(String text, String token, int fromIndex) {
        int len = text.length();
        while (fromIndex < len) {
            int matchIndex = text.indexOf(token, fromIndex);
            if (matchIndex == -1 || matchIndex == fromIndex) {
                return matchIndex;
            }

            // Verify not string-escaped from the fromIndex til matchIndex
            boolean isQuotedString = false;
            char quoteChar = '"';
            boolean escaped = false;
            for(; fromIndex < len; fromIndex++) {
                // When arriving at the next matched part, check if this is escaped or not
                if (fromIndex == matchIndex) {
                    if (!isQuotedString) {
                        return matchIndex;
                    }
                }

                char c = text.charAt(fromIndex);
                if (escaped) {
                    escaped = false;
                } else if (c == '"' || c == '\'') {
                    if (!isQuotedString) {
                        isQuotedString = true;
                        quoteChar = c;
                    } else if (c == quoteChar) {
                        isQuotedString = false;

                        // If beyond the next matched part, break out and search again
                        if (fromIndex > matchIndex) {
                            break;
                        }
                    }
                } else if (isQuotedString && c == '\\') {
                    escaped = true;
                }
            }
        }

        return -1;
    }

    //TODO: Moved to BKCL (UnquotedCharacterFilter)
    private static String escapeString(String text) {
        int len = text.length();
        boolean allowed = true;
        for (int i = 0; i < len; i++) {
            if (!isAllowedInUnquotedString(text.charAt(i))) {
                allowed = false;
                break;
            }
        }
        if (allowed) {
            return text;
        }

        // Escape characters
        StringBuilder escaped = new StringBuilder(len + 8);
        escaped.append('"');
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '"') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        escaped.append('"');
        return escaped.toString();
    }

    //TODO: Moved to BKCL (UnquotedCharacterFilter)
    private static boolean isAllowedInUnquotedString(final char c) {
        return c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c == '_' || c == '-'
                || c == '.' || c == '+';
    }
}
