package com.bergerkiller.bukkit.tc.utils;

/**
 * Implementation of the CircularFIFOQueue that makes use of synchronized
 * methods for both put and take. This means only one write to the buffer
 * can happen at once, but this does simplify things.
 *
 * @param <E> Element type stored in the queue
 */
public class CircularFIFOQueueSynchronized<E> implements CircularFIFOQueue<E> {
    private int writePos;
    private int readPos;
    private Object[] buffer;
    // Is set when abort() is called
    private boolean aborted = false;
    // Is set to true while wait() is busy (synchronized)
    private boolean waiting = false;
    // Callback is called when a put() is done on an empty queue
    // Can be used to kick-start a reading operation on another thread
    private Runnable wakeCallback = () -> {};

    public CircularFIFOQueueSynchronized() {
        this(64);
    }

    public CircularFIFOQueueSynchronized(int initialCapacity) {
        writePos = 0;
        readPos = 0;
        buffer = new Object[initialCapacity];
    }

    @Override
    public int capacity() {
        return buffer.length;
    }

    @Override
    public synchronized void abort() {
        aborted = true;
        notifyAll();
    }

    @Override
    public boolean isAborted() {
        return aborted;
    }

    @Override
    public synchronized void setWakeCallback(Runnable callback) {
        wakeCallback = callback;
    }

    @Override
    public synchronized boolean runIfEmpty(Runnable runnable) {
        if (writePos == readPos) {
            runnable.run();
            return true;
        } else {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized E take(long timeoutMillis) throws EmptyQueueException {
        int rpos;
        if ((rpos = this.readPos) == this.writePos) {
            if (timeoutMillis <= 0 || this.aborted) {
                throw EmptyQueueException.INSTANCE;
            }

            if (timeoutMillis == Long.MAX_VALUE) {
                try {
                    waiting = true;
                    do {
                        if (this.aborted) {
                            throw EmptyQueueException.INSTANCE;
                        }
                        try {
                            this.wait();
                        } catch (InterruptedException e) {}
                    } while ((rpos = this.readPos) == this.writePos);
                } finally {
                    waiting = false;
                }
            } else {
                try {
                    waiting = true;
                    long deadline = System.currentTimeMillis() + timeoutMillis;
                    long remaining = timeoutMillis;
                    while (true) {
                        if (this.aborted) {
                            throw EmptyQueueException.INSTANCE;
                        }
                        try {
                            this.wait(remaining);
                        } catch (InterruptedException e) {}
                        if ((rpos = this.readPos) != this.writePos) {
                            break;
                        } else if ((remaining = (deadline - System.currentTimeMillis())) < 0) {
                            throw EmptyQueueException.INSTANCE;
                        }
                    }
                } finally {
                    waiting = false;
                }
            }
        }

        Object[] buffer = this.buffer;
        if (--rpos < 0) {
            rpos = buffer.length - 1;
        }
        this.readPos = rpos;
        Object value = buffer[rpos];
        buffer[rpos] = null;
        return (E) value;
    }

    @Override
    public synchronized void put(E value) {
        Object[] buffer = this.buffer;
        int read_pos = this.readPos;
        int curr_pos = this.writePos;
        int next_pos = curr_pos - 1;
        if (next_pos < 0) {
            next_pos = buffer.length - 1;
        }
        if (next_pos == read_pos) {
            // Grow the buffer to add more elements
            // Increase by 4/3 size
            Object[] new_buffer = new Object[(buffer.length * 4) / 3];

            // Read all values from buffer and put into new buffer in the same order
            int index = new_buffer.length;
            while (read_pos != curr_pos) {
                if (--read_pos < 0) {
                    read_pos = buffer.length - 1;
                }
                new_buffer[--index] = buffer[read_pos];
            }

            // Swap it over, read from back to front again
            this.buffer = buffer = new_buffer;
            this.readPos = read_pos = 0;
            this.writePos = curr_pos = index;
            next_pos = curr_pos - 1;
            if (next_pos < 0) {
                next_pos = new_buffer.length - 1;
            }
        }

        // Add to buffer and notify anyone if needed
        buffer[next_pos] = value;
        this.writePos = next_pos;
        if (curr_pos == read_pos) {
            if (this.waiting) {
                this.notifyAll();
            } else {
                this.wakeCallback.run();
            }
        }
    }
}
