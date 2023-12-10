package com.bergerkiller.bukkit.tc.utils;

import java.util.function.BooleanSupplier;

/**
 * Caches the result of a boolean supplier, so that successive gets don't repeat
 * the same retrieval.
 */
public final class CachedBooleanSupplier implements BooleanSupplier {
    private final BooleanSupplier getter;
    private Boolean result = null;

    public static CachedBooleanSupplier of(BooleanSupplier supplier) {
        return new CachedBooleanSupplier(supplier);
    }

    private CachedBooleanSupplier(BooleanSupplier supplier) {
        this.getter = supplier;
    }

    @Override
    public boolean getAsBoolean() {
        Boolean result = this.result;
        if (result == null) {
            this.result = result = getter.getAsBoolean();
        }
        return result;
    }
}
