package com.bergerkiller.bukkit.tc.commands.cloud;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Stores the raw list of arguments specified by the user
 */
public class ArgumentList {
    private final String[] args;

    private ArgumentList(Collection<String> args) {
        this.args = args.toArray(new String[0]);
    }

    public List<String> list() {
        return Collections.unmodifiableList(Arrays.asList(args));
    }

    public String[] array() {
        return args;
    }

    public String get(int index) {
        return args[index];
    }

    public boolean has(int index) {
        return index >= 0 && index < args.length;
    }

    public boolean isEmpty() {
        return args.length == 0;
    }

    public int size() {
        return args.length;
    }

    public ArgumentList removeFirst() {
        return range(1, size());
    }

    public ArgumentList removeLast() {
        return range(0, size()-1);
    }

    public ArgumentList range(int fromIndex) {
        return of(Arrays.asList(args).subList(fromIndex, size()));
    }

    public ArgumentList range(int fromIndex, int toIndex) {
        return of(Arrays.asList(args).subList(fromIndex, toIndex));
    }

    public static ArgumentList of(Collection<String> args) {
        return new ArgumentList(args);
    }
}
