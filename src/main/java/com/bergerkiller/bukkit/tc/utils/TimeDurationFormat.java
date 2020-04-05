package com.bergerkiller.bukkit.tc.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formatter for a duration String.
 * Can represent a duration in milliseconds as a String.
 */
public class TimeDurationFormat {
    private final TimeZone timeZone;
    private final SimpleDateFormat sdf;

    /**
     * Creates a new time duration format. The format accepts the same formatting
     * tokens as the Date formatter does.
     * 
     * @param format
     * @throws IllegalArgumentException if the input format is invalid
     */
    public TimeDurationFormat(String format) {
        if (format == null) {
            throw new IllegalArgumentException("Input format should not be null");
        }
        this.timeZone = TimeZone.getTimeZone("GMT+0");
        this.sdf = new SimpleDateFormat(format, Locale.getDefault());
        this.sdf.setTimeZone(this.timeZone);
    }

    /**
     * Formats the duration
     * 
     * @param durationMillis
     * @return formatted string
     */
    public String format(long durationMillis) {
        return this.sdf.format(new Date(durationMillis - this.timeZone.getRawOffset()));
    }
}
