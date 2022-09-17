package com.bergerkiller.bukkit.tc.utils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

/**
 * Implementation of the CircularFIFOQueue that makes use of a
 * {@link StampedLock} to synchronize many simultaneous puts
 * and exclusive takes.
 *
 * @param <E> Element type stored in the queue
 */
public class CircularFIFOQueueStampedRW<E> implements CircularFIFOQueue<E> {
    private final AtomicInteger writePos;
    private int readPos;
    private Object[] buffer;
    private final StampedLock lock;
    // Is set when abort() is called
    private boolean aborted = false;
    // Is set to true while wait() is busy (synchronized)
    private boolean waiting = false;
    // Callback is called when a put() is done on an empty queue
    // Can be used to kick-start a reading operation on another thread
    private Runnable wakeCallback = () -> {};
    // compareAndExchange exists since JDK 9. Adds a slower fallback for JDK 8.
    private static final CompareAndExchangeFunc compareAndExchange = detectCompareAndExchangeFunc();

    public CircularFIFOQueueStampedRW() {
        this(64);
    }

    public CircularFIFOQueueStampedRW(int initialCapacity) {
        writePos = new AtomicInteger(0);
        readPos = 0;
        buffer = new Object[initialCapacity];
        lock = new StampedLock();
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
    public boolean runIfEmpty(Runnable runnable) {
        long slowCheckLock = this.lock.tryWriteLock();
        if (slowCheckLock == 0L) {
            return false; // Someone is currently writing to the queue
        } else {
            try {
                if (this.readPos == this.writePos.get()) {
                    runnable.run();
                    return true;
                } else {
                    return false;
                }
            } finally {
                this.lock.unlockWrite(slowCheckLock);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public E take(long timeoutMillis) throws EmptyQueueException {
        while (true) {
            // Open an exclusive write lock
            // During this time the write index cannot advance
            long slowTakeLock = this.lock.writeLock();
            
            // Check whether there is stuff we can read
            int rpos = this.readPos;
            if (rpos != this.writePos.get()) {
                // We can read stuff!
                Object[] buffer = this.buffer;
                if (--rpos < 0) {
                    rpos = buffer.length - 1;
                }
                this.readPos = rpos;
                Object value = buffer[rpos];
                buffer[rpos] = null;

                // Unlock the lock, we're done with all the dangerous stuff
                this.lock.unlockWrite(slowTakeLock);
                return (E) value;
            }

            // While still write-locked (!) synchronize() ourselves
            // This prevents a potential race condition from happening where
            // someone tries to notify the reader before the reader is actually doing anything
            synchronized (this) {
                // NOW unlock the write lock
                this.lock.unlockWrite(slowTakeLock);

                if (timeoutMillis <= 0 || this.aborted) {
                    throw EmptyQueueException.INSTANCE;
                } else if (timeoutMillis == Long.MAX_VALUE) {
                    try {
                        waiting = true;
                        do {
                            if (this.aborted) {
                                throw EmptyQueueException.INSTANCE;
                            }
                            try {
                                this.wait();
                            } catch (InterruptedException e) {}
                        } while (this.readPos == this.writePos.get());
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
                            if (this.readPos != this.writePos.get()) {
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
        }
    }

    @Override
    public void put(E value) {
        // Non-exclusive lock has the best performance, if it works
        if (!fastPut(value)) {
            long slowPutLock = this.lock.writeLock();
            try {
                slowPut(value);
            } finally {
                this.lock.unlockWrite(slowPutLock);
            }
        }
    }

    /**
     * Tries to put a value using a read lock.
     *
     * @param value Value to put
     * @return true if successful (common), false if there is some problem that can only
     *         be solved with an exclusive lock, like an overflow.
     */
    private boolean fastPut(E value) {
        // Many writers can acquire this at the same time, but they might fail if
        // a conflict is detected while writing.
        long fastPutLock = lock.readLock();

        // Try to decrement the current position without stepping on readPos
        // If we step on readPos, that means the buffer is full and we must handle an overflow exclusively
        // We can also see when we are first to write a value the reader is waiting for
        Object[] buffer = this.buffer;
        int rpos = this.readPos;
        int exchangeResult = this.writePos.get(); // Seed it
        int currWrite, nextWrite;
        do {
            currWrite = exchangeResult;
            nextWrite = currWrite - 1;
            if (nextWrite < 0) {
                nextWrite = buffer.length - 1;
            }
            if (nextWrite == rpos) {
                // Overflow situation! Try to convert our read lock into a write lock.
                // Then we can resolve it right here. If that fails, unlock and try it in
                // a separate routine.
                long slowOverflowPutLock = this.lock.tryConvertToWriteLock(fastPutLock);
                if (slowOverflowPutLock == 0L) {
                    lock.unlockRead(fastPutLock);
                    return false;
                }

                // Handle overflow right here
                try {
                    slowPut(value);
                } finally {
                    lock.unlockWrite(slowOverflowPutLock);
                }
                return true;
            }
        } while ((exchangeResult = compareAndExchange.call(this.writePos, currWrite, nextWrite)) != currWrite);

        // Write next value and unlock
        buffer[nextWrite] = value;
        lock.unlockRead(fastPutLock);

        // If buffer was completely empty, chances are the reader was either stopped or
        // is in a wait situation. Got to notify and wake the reader if it isn't running.
        if (currWrite == rpos) {
            notifyEmpty();
        }

        return true;
    }

    /**
     * A slower put function which always works, but makes use of a slower write lock.
     * Supports dynamic resizing of the buffer thanks to having an exclusive lock.
     *
     * @param value Value to put
     */
    private void slowPut(E value) {
        // Overflow was detected when trying to put a value
        // Now we obtained an exclusive write lock
        // In this critical section we're certain the write / read index won't change

        // Read current next index to write at
        int currWrite = this.writePos.get();
        int nextWrite = currWrite - 1;
        if (nextWrite < 0) {
            nextWrite = this.buffer.length - 1;
        }

        // Check for overflow
        int rpos = this.readPos;
        if (nextWrite == rpos) {
            // Overflow detected
            // Grow the buffer to add more elements
            // Increase to 4/3 the size
            Object[] old_buffer = this.buffer;
            Object[] new_buffer = new Object[(old_buffer.length * 4) / 3];

            // Read all values from buffer and put into new buffer in the same order
            int index = new_buffer.length;
            while (rpos != currWrite) {
                if (--rpos < 0) {
                    rpos = old_buffer.length - 1;
                }
                new_buffer[--index] = old_buffer[rpos];
            }

            // Put the value we want to write
            new_buffer[--index] = value;

            // Swap it over, read from back to front again
            this.buffer = new_buffer;
            this.readPos = 0;
            this.writePos.set(index);
        } else {
            // False alarm. Probably already resolved. Write as usual.
            buffer[nextWrite] = value;
            this.writePos.set(nextWrite);

            // If buffer was completely empty, chances are the reader was either stopped or
            // is in a wait situation. Got to notify and wake the reader if it isn't running.
            if (currWrite == rpos) {
                notifyEmpty();
            }
        }
    }

    private void notifyEmpty() {
        // Check waiting, and if so, wake up the reader thread
        synchronized (this) {
            if (waiting) {
                this.notifyAll();
                return;
            }
        }

        // Not waiting, but might need to start a reader thread
        this.wakeCallback.run();
    }

    private static CompareAndExchangeFunc detectCompareAndExchangeFunc() {
        try {
            AtomicInteger.class.getDeclaredMethod("compareAndExchange", int.class, int.class);
            return AtomicInteger::compareAndExchangeAcquire;
        } catch (Throwable t) {
            return (ai, expectedValue, newValue) -> {
                while (true) {
                    if (ai.compareAndSet(expectedValue, newValue)) {
                        return expectedValue;
                    }
                    int realValue = ai.get();
                    if (realValue != expectedValue) {
                        return realValue;
                    }
                }
            };
        }
    }

    @FunctionalInterface
    private static interface CompareAndExchangeFunc {
        int call(AtomicInteger ai, int expectedValue, int newValue);
    }
}
