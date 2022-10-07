package com.bergerkiller.bukkit.tc.properties;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.bergerkiller.bukkit.tc.Util;

/**
 * Stores the train properties mapped by train name. Also stores a relaxed-name mapping
 * where all train names are lower-cased without style characters, as a backup.
 */
class TrainPropertiesMap {
    private Map<String, TrainProperties> trainProperties = new TreeMap<>();
    // This makes the train names all-lowercase and without chat style characters
    // It allows for people to select trains by a mixed-case or styled name
    private Map<String, List<TrainProperties>> trainPropertiesRelaxed = new TreeMap<>();

    public Collection<TrainProperties> values() {
        return trainProperties.values();
    }

    public TrainProperties get(String trainName) {
        return trainProperties.get(trainName);
    }

    public TrainProperties getRelaxed(String trainName) {
        List<TrainProperties> result = trainPropertiesRelaxed.get(createRelaxedKey(trainName));
        return (result == null || result.size() != 1) ? null : result.get(0);
    }

    public boolean containsKey(String trainName) {
        return trainProperties.containsKey(trainName);
    }

    public void add(String trainName, TrainProperties properties) {
        TrainProperties previous = trainProperties.put(trainName, properties);
        if (previous != null) {
            removeFromRelaxedMappings(trainName, previous);
        }

        String relaxed = createRelaxedKey(trainName);
        List<TrainProperties> prevAtRelaxedKey = trainPropertiesRelaxed.put(relaxed,
                Collections.singletonList(properties));
        if (prevAtRelaxedKey != null) {
            List<TrainProperties> combined = new ArrayList<>(prevAtRelaxedKey);
            combined.add(properties);
            trainPropertiesRelaxed.put(relaxed, combined);
        }
    }

    public TrainProperties remove(String trainName) {
        TrainProperties properties = trainProperties.remove(trainName);
        if (properties != null) {
            removeFromRelaxedMappings(trainName, properties);
        }
        return properties;
    }

    public void clear() {
        trainProperties.clear();
        trainPropertiesRelaxed.clear();
    }

    private void removeFromRelaxedMappings(String trainName, TrainProperties properties) {
        String relaxed = createRelaxedKey(trainName);
        List<TrainProperties> atRelaxedKey = trainPropertiesRelaxed.remove(relaxed);
        if (atRelaxedKey != null) {
            if (atRelaxedKey.size() > 1) {
                // Morethan one match. Keep previous matches.
                atRelaxedKey.remove(properties);
                trainPropertiesRelaxed.put(relaxed, atRelaxedKey);
            } else if (atRelaxedKey.size() == 1 && atRelaxedKey.get(0) != properties) {
                // Put back, wrong properties. Somehow.
                trainPropertiesRelaxed.put(relaxed, atRelaxedKey);
            }
        }
    }

    private static String createRelaxedKey(String trainName) {
        return Util.stripChatStyle(trainName).toLowerCase(Locale.ENGLISH);
    }
}
