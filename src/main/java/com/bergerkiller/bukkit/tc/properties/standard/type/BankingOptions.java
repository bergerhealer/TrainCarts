package com.bergerkiller.bukkit.tc.properties.standard.type;

/**
 * Configures how a train rolls inwards when turning
 */
public final class BankingOptions {
    public static final BankingOptions DEFAULT = new BankingOptions(0.0, 10.0);

    private final double strength;
    private final double smoothness;

    private BankingOptions(double strength, double smoothness) {
        this.strength = strength;
        this.smoothness = smoothness;
    }

    public double strength() {
        return this.strength;
    }

    public double smoothness() {
        return this.smoothness;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(this.strength);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof BankingOptions) {
            BankingOptions other = (BankingOptions) o;
            return this.strength == other.strength &&
                   this.smoothness == other.smoothness;
        } else {
            return false;
        }
    }

    public static BankingOptions create(double strength, double smoothness) {
        return new BankingOptions(strength, smoothness);
    }
}
