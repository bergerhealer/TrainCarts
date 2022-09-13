package com.bergerkiller.bukkit.tc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.junit.Ignore;
import org.junit.Test;

import com.bergerkiller.bukkit.common.bases.IntVector3;

/**
 * Evaluates the performance of the RailCache and in particular
 * the hashing of the blocks inside
 */
public class RailCacheHashingTest {
    // Nice primes: 3, 7, 31, 127, 8191, 131071

    private static int hash(IntVector3 v) {
        int result = v.x;
        result = result * 127 + v.y;
        result = result * 8191 + v.z;
        return result;
        
        //return 127 * v.x + 131071 * v.y + 8191 * v.z;
    }
    
    private static int hash(IntVector3 v, int fx, int fy, int fz) {
        return hash(v);

        //return fx * v.x + fy * v.y + fz * v.z;
        
        /*
        int result = fx + v.x;
        result = fy * result + v.z;
        result = fz * result + v.y;
        return result;
        */
    }

    @Ignore
    @Test
    public void testHashmapPerformance() throws IOException {
        String contents = Files.readString(Path.of("hashtest/eruvedozig.txt"));
        List<IntVector3> blocks = Arrays.stream(contents.split("\n")).map(
                line -> Arrays.stream(line.split(" "))
                        .mapToInt(s -> {
                            try {
                                return Integer.parseInt(s.replace("\r", "").replace("\n", ""));
                            } catch (NumberFormatException ex) {
                                throw new IllegalStateException("Bad number: " + s);
                            }
                        }).toArray())
                .map(coord -> new IntVector3(coord[0], coord[1], coord[2]))
                .toList();

        HashMap<IntVector3, String> values = new HashMap<>(2048, 0.75f);
        for (IntVector3 v : blocks) {
            values.put(v, v.toString());
        }

        for (int n = 0; n < 50000; n++) {
            for (IntVector3 v : blocks) {
                values.get(v);
            }
        }
        
        /*
        Object2ObjectOpenHashMap<IntVector3, String> values = new Object2ObjectOpenHashMap<>(2048, 0.5f);
        for (IntVector3 v : blocks) {
            values.put(v, v.toString());
        }

        for (int n = 0; n < 50000; n++) {
            for (IntVector3 v : blocks) {
                values.get(v);
            }
        }
        */
        
        //Object2ObjectOpenCustomHashMap
    }
    
    @Ignore
    @Test
    public void testHashPerformance() throws IOException {
        String contents = Files.readString(Path.of("hashtest/eruvedozig.txt"));
        List<IntVector3> blocks = Arrays.stream(contents.split("\n")).map(
                line -> Arrays.stream(line.split(" "))
                        .mapToInt(s -> {
                            try {
                                return Integer.parseInt(s.replace("\r", "").replace("\n", ""));
                            } catch (NumberFormatException ex) {
                                throw new IllegalStateException("Bad number: " + s);
                            }
                        }).toArray())
                .map(coord -> new IntVector3(coord[0], coord[1], coord[2]))
                .toList();

        long k = 0;
        for (int w = 0; w < 100; w++) {
            for (IntVector3 block : blocks) {
                for (int n = 0; n < 20000; n++) {
                    k += hash(block);
                }
            }
        }
        System.out.println(k);
    }

    @Ignore
    @Test
    public void testAnalyzeBlocks() throws IOException {
        String contents = Files.readString(Path.of("hashtest/eruvedozig.txt"));
        List<IntVector3> blocks = Arrays.stream(contents.split("\n")).map(
                line -> Arrays.stream(line.split(" "))
                        .mapToInt(s -> {
                            try {
                                return Integer.parseInt(s.replace("\r", "").replace("\n", ""));
                            } catch (NumberFormatException ex) {
                                throw new IllegalStateException("Bad number: " + s);
                            }
                        }).toArray())
                .map(coord -> new IntVector3(coord[0], coord[1], coord[2]))
                .toList();

        System.out.println("Input: " + blocks.size() + " blocks");

        int tableSize = tableSizeFor(blocks.size(), 0.75);
        int mask = tableSize - 1;
        System.out.println("Table size: " + tableSize);

        /*

Found best fx= 8079 fy=3326 fz=7627
There are 7740 unique hashes


Found best fx= 31 fy=9941 fz=3217
There are 7612 unique hashes

Found best fx=110503 fy=3 fz=5
There are 6597 unique hashes

Found best fx=13 fy=127 fz=607
There are 7511 unique hashes

Found best fx=607 fy=7 fz=21701
There are 7582 unique hashes
         */
        
        int searchStart = 1;
        int searchCount = 100000;

        HashFinder best = IntStream.range(0, 10)
                .mapToObj(n -> new HashFinder(tableSize, blocks, searchStart + n * searchCount, searchCount))
                .parallel()
                .map(HashFinder::search)
                .max(HashFinder::compare)
                .get();
        System.out.println("Found best fx=" + best.bestFX + " fy=" + best.bestFY + " fz=" + best.bestFZ);

        // Compute actual groups using found fx/fz
        HashMap<Integer, List<IntVector3>> groups = new HashMap();
        for (IntVector3 block : blocks) {
            int blockHash = mask & hash(block, best.bestFX, best.bestFY, best.bestFZ);
            List<IntVector3> group = groups.computeIfAbsent(blockHash, a -> new ArrayList<>());
            group.add(block);
        }
        System.out.println("There are " + groups.size() + " unique hashes");

        // Produce a sorted list of group sizes
        TreeMap<Integer, Integer> distribution = new TreeMap<>();
        for (List<IntVector3> group : groups.values()) {
            distribution.compute(group.size(), (size, previous) -> {
                return (previous == null) ? 1 : previous + 1;
            });
        }
        ArrayList<Map.Entry<Integer, Integer>> distributionList = new ArrayList<>(distribution.entrySet());
        Collections.reverse(distributionList);

        System.out.println("Top 20 hash collisions:");
        for (int i = 0; i < Math.min(20, distributionList.size()); i++) {
            Map.Entry<Integer, Integer> group = distributionList.get(i);
            System.out.println("- " + group.getKey() + " items: " + group.getValue() + " times");
        }

        // Show largest group
        List<IntVector3> largestGroup = groups.values().stream()
                .sorted((a, b) -> Integer.compare(b.size(), a.size()))
                .findFirst().get();
        System.out.println("Largest group (" + largestGroup.size() + " items):");
        for (IntVector3 block : largestGroup) {
            System.out.println("- [ " + block.x + ", " + block.y + ", " + block.z + "]");
        }
    }

    @Ignore
    @Test
    public void testAnalyzeAllFiles() throws IOException {
        //3, 7, 31, 127, 8191, 131071
        

        int fx = 127;
        int fy = 31;
        int fz = 8191;


        /*
        int fx = 3;
        int fy = 127;
        int fz = 131071;
        */
        
        for (Path file : Files.list(Path.of("hashtest")).toList()) {
            String contents = Files.readString(file);
            List<IntVector3> blocks = Arrays.stream(contents.split("\n")).map(
                    line -> Arrays.stream(line.split(" "))
                            .mapToInt(s -> {
                                try {
                                    return Integer.parseInt(s.replace("\r", "").replace("\n", ""));
                                } catch (NumberFormatException ex) {
                                    throw new IllegalStateException("Bad number: " + s);
                                }
                            }).toArray())
                    .map(coord -> new IntVector3(coord[0], coord[1], coord[2]))
                    .toList();

            int tableSize = tableSizeFor(blocks.size(), 0.75);
            int mask = tableSize - 1;

            // Compute actual groups using found fx/fz
            HashMap<Integer, List<IntVector3>> groups = new HashMap();
            for (IntVector3 block : blocks) {
                int blockHash = mask & hash(block, fx, fy, fz);
                List<IntVector3> group = groups.computeIfAbsent(blockHash, a -> new ArrayList<>());
                group.add(block);
            }

            // Produce a sorted list of group sizes
            TreeMap<Integer, Integer> distribution = new TreeMap<>();
            for (List<IntVector3> group : groups.values()) {
                distribution.compute(group.size(), (size, previous) -> {
                    return (previous == null) ? 1 : previous + 1;
                });
            }
            ArrayList<Map.Entry<Integer, Integer>> distributionList = new ArrayList<>(distribution.entrySet());
            Collections.reverse(distributionList);

            System.out.println("Performance [" + file.getFileName() + "] " + ((int) ((double) groups.size() * 100.0 / blocks.size())) + "% - " + groups.size() + " / " + blocks.size() + ":");
            for (int i = 0; i < Math.min(20, distributionList.size()); i++) {
                Map.Entry<Integer, Integer> group = distributionList.get(i);
                System.out.println("- " + group.getKey() + " items: " + group.getValue() + " times");
            }
        }
    }
    
    /**
     * Returns a power of two size for the given target capacity.
     */
    public static final int tableSizeFor(int cap, double loadFactor) {
        cap = (int) ((1.0 / loadFactor) * cap);
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= (1<<30)) ? (1<<30) : n + 1;
    }

    public static class HashFinder {
        private final boolean[] state;
        private final List<IntVector3> blocks;
        private final int searchStart;
        private final int searchCount;
        public int bestCount = 0;
        public int bestFX = 0;
        public int bestFY = 0;
        public int bestFZ = 0;

        public HashFinder(int tableSize, List<IntVector3> blocks, int searchStart, int searchCount) {
            this.state = new boolean[tableSize];
            this.blocks = blocks;
            this.searchStart = searchStart;
            this.searchCount = searchCount;
        }

        public HashFinder search() {
            int mask = this.state.length - 1;
            int searchEnd = this.searchStart + this.searchCount;
            int bestCount = 0;
            Random rand = new Random();
            int max_fact = 100000;
            for (int n = this.searchStart; n < searchEnd; n++) {
                int fx = rand.nextInt(max_fact);
                int fy = rand.nextInt(max_fact);
                int fz = rand.nextInt(max_fact);
                for (IntVector3 b : blocks) {
                    this.state[mask & hash(b, fx, fy, fz)] = true;
                }
                int count = 0;
                for (int k = 0; k <= mask; k++) {
                    if (this.state[k]) {
                        this.state[k] = false;
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    this.bestCount = bestCount;
                    this.bestFX = fx;
                    this.bestFY = fy;
                    this.bestFZ = fz;
                }
            }
            this.bestCount = bestCount;
            System.out.println("Count " + bestCount + " fx= " + bestFX + " fy=" + bestFY + " fz=" + bestFZ);
            return this;
        }

        public static int compare(HashFinder a, HashFinder b) {
            int n = Integer.compare(a.bestCount, b.bestCount);
            return (n == 0) ? Integer.compare(b.bestFX + b.bestFY + b.bestFZ, a.bestFX + a.bestFY + a.bestFZ) : n;
        }
    }
}
