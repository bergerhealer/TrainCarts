package com.bergerkiller.bukkit.tc.properties.standard.type;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores the train name format used to name a train.
 * Handles the automatic generation of train names, using
 * an integer counter. Also offers a method to check whether
 * a given train name matches that format.
 */
public final class TrainNameFormat {
    /** Default train name format, which uses train# */
    public static final TrainNameFormat DEFAULT = new TrainNameFormat("train", "", false);
    /** Regex pattern to find the last digits used in a name, used when guessing */
    private static final Pattern NAME_GUESS_PATTERN = Pattern.compile("^(.*?)\\d+([^\\d]*)$");

    private final String _prefix;
    private final String _postfix;
    private final boolean _optionalNumber;

    private TrainNameFormat(String prefix, String postfix, boolean optionalNumber) {
        this._prefix = prefix;
        this._postfix = postfix;
        this._optionalNumber = optionalNumber;
    }

    /**
     * Gets whether this format defines an optional number. This means
     * the format will only include a number somewhere if another train
     * exactly matches this name. This is the case when the generation
     * number is greater than one.
     *
     * @return True if the number is appended as an optional step
     */
    public boolean hasOptionalNumber() {
        return _optionalNumber;
    }

    /**
     * Generates a train name using this train name format.
     * The input number specifies the iteration of the name, where
     * the first iteration should start at 1.
     *
     * @param number Number of iterations
     * @return generated train name
     */
    public String generate(int number) {
        if (_optionalNumber && number <= 1) {
            return _prefix;
        } else {
            StringBuilder str = new StringBuilder(
                    _prefix.length() + _postfix.length() + 5);
            str.append(_prefix);
            str.append(number);
            str.append(_postfix);
            return str.toString();
        }
    }

    /**
     * Searches for a unique name generated using this format that
     * passes a filter
     *
     * @param filter Name filter
     * @return Name that passes the filter
     */
    public String search(Predicate<String> filter) {
        for (int number = 1;; number++) {
            String name = generate(number);
            if (filter.test(name)) {
                return name;
            }
        }
    }

    /**
     * Checks whether the given train name matches this train name format.
     * This means the train name could possibly have been generated
     * using this format.
     *
     * @param trainName Train name to check
     * @return True if the name matches this train name format, False otherwise
     */
    public boolean matches(String trainName) {
        // No number used (optional)
        if (_optionalNumber && trainName.equals(_prefix)) {
            return true;
        }

        // Should match prefix and postfix
        if (!trainName.startsWith(_prefix) || !trainName.endsWith(_postfix)) {
            return false;
        }

        // Characters between prefix and postfix should all be digits
        int end = trainName.length() - _postfix.length();
        if (end == _prefix.length()) {
            return false; // no characters between prefix and postfix
        }
        for (int i = _prefix.length(); i < end; i++) {
            if (!Character.isDigit(trainName.charAt(i))) {
                return false;
            }
        }

        // All good!
        return true;
    }

    /**
     * Parses a train name format. The #-character in the format denotes
     * where in the format a random number should be inserted to
     * make the name unique. If no such characters are included, a number
     * is appended at the end.<br>
     * <br>
     * For multiple instances of the #-character existing, only the last
     * instance of it will be replaced with a number digit. This means
     * that '##' in the format will result in '#12' as the final name.
     *
     * @param format Input format to parse
     * @return Parsed train name format
     */
    public static TrainNameFormat parse(String format) {
        // Find the last instance of # being used (that is not escaped)
        int lastHashIndex = format.lastIndexOf('#');
        if (lastHashIndex == -1) {
            return new TrainNameFormat(format, "", true);
        } else {
            return new TrainNameFormat(
                    format.substring(0, lastHashIndex),
                    format.substring(lastHashIndex + 1),
                    false);
        }
    }

    /**
     * Attempts to guess the most likely format used to
     * generate a given train name. It does so by detecting
     * digits in the name, and assuming those are number placeholders.
     *
     * @param trainName Name to guess the format of
     * @return Train name format that matches the train name
     */
    public static TrainNameFormat guess(String trainName) {
        Matcher matcher = NAME_GUESS_PATTERN.matcher(trainName);
        if (matcher.find()) {
            return new TrainNameFormat(matcher.group(1), matcher.group(2), false);
        } else {
            return new TrainNameFormat(trainName, "", true);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TrainNameFormat) {
            TrainNameFormat other = (TrainNameFormat) o;
            return _prefix.equals(other._prefix)
                    && _postfix.equals(other._postfix)
                    && _optionalNumber == other._optionalNumber;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        if (_optionalNumber) {
            return _prefix + _postfix;
        } else {
            return _prefix + "#" + _postfix;
        }
    }
}
