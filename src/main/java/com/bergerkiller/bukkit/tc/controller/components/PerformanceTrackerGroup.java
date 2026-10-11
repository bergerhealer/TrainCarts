package com.bergerkiller.bukkit.tc.controller.components;

import org.bukkit.ChatColor;

/**
 * Tracks the performance of the updates performed on a single train.
 */
public final class PerformanceTrackerGroup {
    private static final int WINDOW_TICKS = 30 * 20;

    private final long[] samples = new long[WINDOW_TICKS];
    private int position = 0;
    private int count = 0;
    private double cachedAverageMsptPercentage = 0.0;
    private double cachedPeakMsptPercentage = 0.0;
    private boolean dirty = true;

    /**
     * Gets the average train update time in milliseconds per tick, over roughly the
     * last 30 seconds of samples.
     *
     * @return average mspt
     */
    public double getAverageMsptPercentage() {
        this.computeStats();
        return this.cachedAverageMsptPercentage;
    }

    /**
     * Gets the peak train update time in milliseconds per tick, over roughly the
     * last 30 seconds of samples.
     *
     * @return peak mspt
     */
    public double getPeakMsptPercentage() {
        this.computeStats();
        return this.cachedPeakMsptPercentage;
    }

    /**
     * Gets the standard mspt / peak mspt percentages as an output text string.
     * Used for the hover tooltip in train list, and in train info.
     *
     * @return Stats string
     */
    public String getStatsMessage() {
        StringBuilder str = new StringBuilder();
        appendStatsMessage(str);
        return str.toString();
    }

    /**
     * Appends the standard mspt / peak mspt percentages to an output text string.
     * Used for the hover tooltip in train list, and in train info.
     *
     * @param str Str to append to
     */
    public void appendStatsMessage(StringBuilder str) {
        this.computeStats();
        str.append(ChatColor.GREEN).append("Average MsPt: ");
        appendMsPtPercentage(str, this.cachedAverageMsptPercentage);
        str.append(ChatColor.GREEN).append(" (");
        appendMsPtPercentage(str, this.cachedPeakMsptPercentage);
        str.append(ChatColor.GREEN).append(" peak)");
    }

    private static void appendMsPtPercentage(StringBuilder str, double msptPercentage) {
        if (msptPercentage > 5) {
            str.append(ChatColor.RED);
        } else if (msptPercentage > 1) {
            str.append(ChatColor.YELLOW);
        } else {
            str.append(ChatColor.GREEN);
        }

        str.append(String.format(java.util.Locale.ENGLISH, "%.1f", msptPercentage));
        str.append('%');
    }

    /**
     * Pushes a new sample of the current update tick
     *
     * @param nanos Total amount of nanoseconds the update took
     */
    public void pushTickTimes(long nanos) {
        this.samples[this.position] = nanos;
        if (++this.position >= WINDOW_TICKS) {
            this.position = 0;
        }
        if (this.count < WINDOW_TICKS) {
            this.count++;
        }
        this.dirty = true;
    }

    private void computeStats() {
        if (!this.dirty) {
            return;
        }
        if (this.count == 0) {
            this.cachedAverageMsptPercentage = 0.0;
            this.cachedPeakMsptPercentage = 0.0;
        } else {
            long total = 0L;
            long peak = 0L;
            for (int i = 0; i < this.count; i++) {
                long sample = this.samples[i];
                total += sample;
                if (sample > peak) {
                    peak = sample;
                }
            }
            this.cachedAverageMsptPercentage = nanosToMsPercentage(total / this.count);
            this.cachedPeakMsptPercentage = nanosToMsPercentage(peak);
        }
        this.dirty = false;
    }

    private static double nanosToMsPercentage(long nanos) {
        double ms = nanos / 1_000_000.0;
        return 100.0 * ms / 50.0; // Percentage of a full 50ms tick
    }
}
