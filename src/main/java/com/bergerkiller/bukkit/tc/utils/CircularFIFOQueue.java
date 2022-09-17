package com.bergerkiller.bukkit.tc.utils;

import java.util.function.Consumer;

/**
 * A dynamically-sized ring buffer that can put values from multiple threads
 * and take values from it from a single thread (Multi-producer single-consumer).
 * Has an {@link #abort()} method to shut down a reader thread gracefully.<br>
 * <br>
 * A guarantee is made that all values put from a single thread will be read
 * out again in the same order, regardless of whether or not the FIFO queue
 * is resized during this process.<br>
 * <br>
 * This queue should only be used when there are a lot of tasks being pushed
 * from many different threads, and a single processing thread is processing all
 * these items one at a time. Works best with slow readers, where the queue will
 * automatically resize itself to fit demand.
 *
 * @param <E> Element type stored in the queue
 */
public interface CircularFIFOQueue<E> {

    /**
     * Gets the current capacity of this queue. This is the number of items that
     * can be stored that haven't been taken yet. Resizes automatically.
     *
     * @return current queue capacity
     */
    int capacity();

    /**
     * Aborts {@link #take(long)} early once the queue is empty.
     * If entries still need to be read, those will be read out first.
     * When aborted this disables take() from ever waiting.
     */
    void abort();

    /**
     * Gets whether {@link #abort()} was called
     *
     * @return True if this circular fifo queue has been aborted
     */
    boolean isAborted();

    /**
     * Checks whether this circular FIFO queue is empty. Is only reliable to
     * call when no more {@link #put(Object)} actions are being performed.
     *
     * @return True if this queue is empty
     */
    default boolean isEmpty() {
        return this.runIfEmpty(() -> {});
    }

    /**
     * Sets a callback to be called when {@link #put(Object)} is done while this
     * circular queue is empty. Can be used to kickstart a reader thread
     * if one is not already running. The callback is synchronized, and only
     * one instance will run at any one time.
     *
     * @param callback
     */
    void setWakeCallback(Runnable callback);

    /**
     * Runs an action if this queue is empty. Can be used with
     * {@link #setWakeCallback(Runnable)} right before shutting down a reader
     * thread to make sure the reader, actually, should be shut down.
     * The runnable is synchronized in the same way <i>setWakeCallback</i> is,
     * and as such can update the same shared state.<br>
     * <br>
     * Typically inside the runnable a flag is updated that tells a future
     * wake callback that the task should be started again.
     *
     * @param runnable Runnable to be run synchronized if this queue is empty
     * @return True if the runnable was actually run because the queue was empty
     */
    boolean runIfEmpty(Runnable runnable);

    /**
     * Takes a value from this FIFO queue. Not multi-thread safe, only
     * one thread should be taking values from this queue at a time.<br>
     * <br>
     * If the queue is empty, waits indefinitely until an item is added
     * or {@link #abort()} is called.
     *
     * @return value taken
     * @throws EmptyQueueException When {@link #abort()} was called
     *         and the queue is empty.
     */
    default E take() throws EmptyQueueException {
        return take(Long.MAX_VALUE);
    }

    /**
     * Takes a value from this FIFO queue. Not multi-thread safe, only
     * one thread should be taking values from this queue at a time.
     *
     * @param timeoutMillis When the queue is empty for this long, throws an
     *                      {@link EmptyQueueException}. Use 0 to fail instantly.
     * @return value taken
     * @throws EmptyQueueException When timeout is reached or {@link #abort()} was called
     *                             and the queue is empty.
     */
    E take(long timeoutMillis) throws EmptyQueueException;

    /**
     * Puts values into this FIFO queue. Is multithread-safe.
     * Values put from the same thread will guaranteed be read out in
     * the same order they were put in.
     *
     * @param value Value to put
     */
    void put(E value);

    /**
     * Returns an inactive CircularFIFOQueue which forwards all {@link #put(Object)} calls
     * to a consumer. All other methods will fail and act like the queue is empty.
     *
     * @param <E> Element type
     * @param consumer Consumer to call with all put calls
     * @return CircularFIFOQueue
     */
    public static <E> CircularFIFOQueue<E> forward(final Consumer<E> consumer) {
        return new CircularFIFOQueue<E>() {
            @Override
            public int capacity() {
                return 0;
            }

            @Override
            public void abort() {
            }

            @Override
            public boolean isAborted() {
                return true;
            }

            @Override
            public void setWakeCallback(Runnable callback) {
            }

            @Override
            public boolean isEmpty() {
                return true;
            }

            @Override
            public boolean runIfEmpty(Runnable runnable) {
                runnable.run();
                return true;
            }

            @Override
            public E take(long timeoutMillis) throws EmptyQueueException {
                throw EmptyQueueException.INSTANCE;
            }

            @Override
            public void put(E value) {
                consumer.accept(value);
            }
        };
    }

    /**
     * Exception thrown when the FIFO queue is empty and either the timeout was reached,
     * or the {@link CircularFIFOQueue#abort()} method was called.
     */
    public static final class EmptyQueueException extends Exception {
        /**
         * Static singleton instance of this exception, to avoid overhead of construction
         */
        public static final EmptyQueueException INSTANCE = new EmptyQueueException();
        private static final long serialVersionUID = 1824696362338789498L;

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
