package com.bergerkiller.bukkit.tc.properties.standard.type;

/**
 * Stores a default configuration to apply to trains to set the displayed
 * block metadata of all minecarts. Only works with vanilla minecarts, and is
 * kind of useless now tha the attachment editor exists. Here for legacy integration.
 */
public final class TrainDisplayedBlocks {
    public static final int BLOCK_OFFSET_NONE = Integer.MAX_VALUE;
    public static final TrainDisplayedBlocks DEFAULT = new TrainDisplayedBlocks("", BLOCK_OFFSET_NONE);
    private final String typesPattern;
    private final int offset;

    private TrainDisplayedBlocks(String typesPattern, int offset) {
        this.typesPattern = typesPattern;
        this.offset = offset;
    }

    public String getBlockTypesPattern() {
        return typesPattern;
    }

    public int getOffset() {
        return offset;
    }

    @Override
    public int hashCode() {
        return typesPattern.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TrainDisplayedBlocks) {
            TrainDisplayedBlocks options = (TrainDisplayedBlocks) o;
            return this.typesPattern.equals(options.typesPattern) &&
                   this.offset == options.offset;
        } else {
            return false;
        }
    }

    public static TrainDisplayedBlocks of(String typesPattern, int offset) {
        return new TrainDisplayedBlocks(typesPattern, offset);
    }
}
