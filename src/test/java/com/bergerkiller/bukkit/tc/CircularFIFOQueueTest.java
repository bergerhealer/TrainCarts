package com.bergerkiller.bukkit.tc;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.Test;

import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueue;
import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueue.EmptyQueueException;
import com.bergerkiller.bukkit.tc.utils.CircularFIFOQueueSynchronized;

public class CircularFIFOQueueTest {

    private <T> CircularFIFOQueue<T> createQueue() {
        return new CircularFIFOQueueSynchronized<T>();
    }

    @Test
    public void testBenchmark() {
        final CircularFIFOQueue<String> queue = createQueue();
        Thread reader = new Thread() {
            @Override
            public void run() {
                while (true) {
                    String value;
                    try {
                        value = queue.take();
                    } catch (EmptyQueueException e) {
                        break;
                    }
                    busy(value);
                }
            }
        };
        reader.start();

        // One [1000]: 2873ms 1755ms 2153ms
        // Two [1000]: 548ms 552ms 521ms
        // Three [1000]: 471ms 451ms
        final int putCount = 1000;

        // Heat up
        writeToQueue(queue, putCount);

        long total = 0;
        for (int n = 0; n < 10; n++) {
            total += writeToQueue(queue, putCount);
        }


        System.out.println("TOOK: " + ((double) total / 1000000.0) + "ms");

        // Shut down the reader
        queue.abort();
        try {
            reader.join();
        } catch (InterruptedException e) {}
    }

    private long writeToQueue(final CircularFIFOQueue<String> queue, final int putCount) {
        List<? extends Thread> threads = IntStream.range(0, 10).mapToObj(i -> new Thread() {
            @Override
            public void run() {
                for (int k = 0; k < putCount; k++) {
                    queue.put("a");
                    queue.put("b");
                    queue.put("c");
                }
            }
        }).toList();

        threads.forEach(Thread::start);
        long a = System.nanoTime();
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
        long b = System.nanoTime();

        // Wait until reader is done
        while (!queue.isEmpty()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {}
        }
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {}

        return (b - a);
    }

    private static void busy(String value) {
        String s = "";
        for (int n = 0; n < 100; n++) {
            s += value;
        }
        s.hashCode();
    }
    
    @Test
    public void testSimple() throws CircularFIFOQueue.EmptyQueueException {
        CircularFIFOQueue<String> queue = createQueue();
        queue.put("a");
        queue.put("b");
        queue.put("c");
        assertEquals("a", queue.take());
        assertEquals("b", queue.take());
        assertEquals("c", queue.take());

        try {
            queue.take(0L);
            fail("Take succeeded, it shouldn't have");
        } catch (CircularFIFOQueue.EmptyQueueException ex) {
            // Pass
        }
    }

    @Test
    public void testCircular() throws CircularFIFOQueue.EmptyQueueException {
        CircularFIFOQueue<String> queue = createQueue();
        int initialCapacity = queue.capacity();
        for (int n = 0; n < 100; n++) {
            queue.put("a");
            queue.put("b");
            queue.put("c");
            assertEquals("a", queue.take());
            assertEquals("b", queue.take());
            assertEquals("c", queue.take());
        }

        // Capacity should not have increased as we had balanced put/take
        assertEquals(initialCapacity, queue.capacity());
    }

    @Test
    public void testOverflow() throws CircularFIFOQueue.EmptyQueueException {
        CircularFIFOQueue<Integer> queue = createQueue();
        for (int w = 0; w < 10; w++) {
            // Run 10x to make sure things don't corrupt after the overflow
            int capacity = queue.capacity() + 64;
            for (int n = 0; n < capacity; n++) {
                queue.put(n);
            }
            for (int n = 0; n < capacity; n++) {
                assertEquals(n, queue.take().intValue());
            }
        }
    }

    @Test
    public void testAbort() throws CircularFIFOQueue.EmptyQueueException {
        final CircularFIFOQueue<String> queue = createQueue();
        queue.put("a");
        queue.put("b");
        queue.put("c");
        queue.abort();
        assertEquals("a", queue.take());
        assertEquals("b", queue.take());
        assertEquals("c", queue.take());
        try {
            queue.take();
            fail("Take succeeded, it shouldn't have");
        } catch (CircularFIFOQueue.EmptyQueueException ex) {
            // Pass
        }
    }

    @Test
    public void testMultithreadedSlowReader() throws CircularFIFOQueue.EmptyQueueException {
        // Writing from 10 threads but only reading from one. Causes frequent overflows
        // and has lots of potential for any corruption to show itself.
        final CircularFIFOQueue<Integer> queue = createQueue();
        final int amount = 10000;

        List<Thread> workers = IntStream.range(0, 10)
                .mapToObj(n -> {
                    Thread worker = new Thread() {
                        @Override
                        public void run() {
                            int offset = n * amount;
                            for (int k = 0; k < amount; k++) {
                                queue.put(k + offset);
                            }
                        }
                    };
                    return worker;
                })
                .toList();

        workers.forEach(Thread::start);

        Set<Integer> readValues = new HashSet<>();
        for (int n = 0; n < (10*amount); n++) {
            Integer value = queue.take();
            if (!readValues.add(value)) {
                throw new IllegalStateException("Read " + value + " twice");
            }
        }

        workers.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {}
        });
    }

    @Test
    public void testMultithreadedSlowWriter() throws CircularFIFOQueue.EmptyQueueException {
        // Reads faster than we write. Results in use of the wait object,
        // so any deadlocks will be detected here.
        final CircularFIFOQueue<Integer> queue = createQueue();
        final int numWriters = 10;
        final int amount = 100;

        List<Thread> workers = IntStream.range(0, numWriters)
                .mapToObj(n -> {
                    Thread worker = new Thread() {
                        @Override
                        public void run() {
                            int offset = n * amount;
                            int sleepCnt = 0;
                            for (int k = 0; k < amount; k++) {
                                queue.put(k + offset);
                                if (++sleepCnt == 10) {
                                    sleepCnt = 0;
                                    try {
                                        Thread.sleep(5);
                                    } catch (InterruptedException e) {}
                                }
                            }
                        }
                    };
                    return worker;
                })
                .toList();

        workers.forEach(Thread::start);

        Set<Integer> readValues = new HashSet<>(numWriters*amount);
        for (int n = 0; n < (numWriters*amount); n++) {
            Integer value = queue.take();
            if (!readValues.add(value)) {
                throw new IllegalStateException("Read " + value + " twice");
            }
        }

        workers.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {}
        });
    }

    @Test
    public void testWakeCallback() throws CircularFIFOQueue.EmptyQueueException {
        final CircularFIFOQueue<String> queue = createQueue();
        final AtomicInteger wakeCounter = new AtomicInteger();
        queue.setWakeCallback(wakeCounter::incrementAndGet);

        assertEquals(0, wakeCounter.get());
        queue.put("a");
        assertEquals(1, wakeCounter.get());
        queue.put("b");
        assertEquals(1, wakeCounter.get());
        queue.put("c");
        assertEquals(1, wakeCounter.get());
        queue.take();
        assertEquals(1, wakeCounter.get());
        queue.take();
        assertEquals(1, wakeCounter.get());
        queue.take();
        assertEquals(1, wakeCounter.get());
        queue.put("a");
        assertEquals(2, wakeCounter.get());
        queue.put("b");
        assertEquals(2, wakeCounter.get());
        queue.put("c");
        assertEquals(2, wakeCounter.get());
    }
}
