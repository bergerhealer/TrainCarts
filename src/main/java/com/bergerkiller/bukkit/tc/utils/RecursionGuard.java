package com.bergerkiller.bukkit.tc.utils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple Recursion Guard. Implement {@link Handler#onRecursion(Object)} to log a stack or log
 * a warning about the recursion. Use this class with try-with-resources.
 *
 * @param <T> Value type
 */
public final class RecursionGuard<T> {
    private final AtomicBoolean opened = new AtomicBoolean(false);
    private static final Token INACTIVE_TOKEN = () -> {};
    private final Token ACTIVE_TOKEN = () -> opened.set(false);
    private final Handler<T> handler;

    private RecursionGuard(Handler<T> handler) {
        this.handler = handler;
    }

    public static <T> RecursionGuard<T> handleOnce(Handler<T> handler) {
        return handle(new Handler<T>() {
            private final AtomicBoolean handled = new AtomicBoolean(false);

            @Override
            public void onRecursion(T value) {
                if (handled.compareAndSet(false, true)) {
                    handler.onRecursion(value);
                }
            }
        });
    }

    public static <T> RecursionGuard<T> handle(Handler<T> handler) {
        return new RecursionGuard<>(handler);
    }

    /**
     * Opens this recursion guard
     *
     * @param value Value to pass to {@link Handler#onRecursion(Object)} if a recursion is detected
     * @return AutoCloseable Token, use it with try-with-resources
     */
    public Token open(T value) {
        if (opened.compareAndSet(false, true)) {
            return ACTIVE_TOKEN;
        } else {
            handler.onRecursion(value);
            return INACTIVE_TOKEN;
        }
    }

    @FunctionalInterface
    public interface Handler<T> {
        /**
         * Called when a recursion is detected
         *
         * @param value Input argument to {@link #open(Object)}
         */
        void onRecursion(T value);
    }

    public interface Token extends AutoCloseable {
        @Override
        void close();
    }
}
