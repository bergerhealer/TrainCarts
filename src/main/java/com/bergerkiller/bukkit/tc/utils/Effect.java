package com.bergerkiller.bukkit.tc.utils;

import com.bergerkiller.bukkit.common.collections.StringMap;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.common.utils.StringUtil;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Effect {
    private static final StringMap<Integer> DIR_NAMES = new StringMap<>();
    private static final StringMap<Integer> DISK_NAMES = new StringMap<>();

    static {
        // Smoke
        DIR_NAMES.putUpper("U", 4);
        DIR_NAMES.putUpper("M", 4);
        DIR_NAMES.putUpper("N", 7);
        DIR_NAMES.putUpper("E", 3);
        DIR_NAMES.putUpper("S", 1);
        DIR_NAMES.putUpper("W", 5);
        DIR_NAMES.putUpper("NE", 6);
        DIR_NAMES.putUpper("SE", 0);
        DIR_NAMES.putUpper("NW", 8);
        DIR_NAMES.putUpper("SW", 2);
        // Record disks
        DISK_NAMES.putUpper("NONE", 0);
        DISK_NAMES.putUpper("13", 2256);
        DISK_NAMES.putUpper("CAT", 2257);
        DISK_NAMES.putUpper("BLOCKS", 2258);
        DISK_NAMES.putUpper("CHIRP", 2259);
        DISK_NAMES.putUpper("FAR", 2260);
        DISK_NAMES.putUpper("MALL", 2261);
        DISK_NAMES.putUpper("MELLOHI", 2262);
        DISK_NAMES.putUpper("STAL", 2263);
        DISK_NAMES.putUpper("STRAD", 2264);
        DISK_NAMES.putUpper("WARD", 2265);
        DISK_NAMES.putUpper("11", 2266);
        DISK_NAMES.putUpper("WAIT", 2267);
    }

    public final List<String> effects = new ArrayList<>();
    public float pitch = 1.0f, volume = 1.0f;
    public int range;

    public void parseEffect(String text) {
        text = text.toUpperCase(Locale.ENGLISH).replace(' ', '_');
        text = text.replace("MUSIC", "RECORD");
        if (text.equals("LINK")) {
            this.effects.add("SMOKE");
            this.effects.add("EXTINGUISH");
            return;
        }
        this.effects.add(text);
    }

    private String trimSpace(String text) {
        return text.substring(StringUtil.getSuccessiveCharCount(text, '_'));
    }

    private void play(Location location, org.bukkit.Effect effect, int data) {
        try {
            location.getWorld().playEffect(location, effect, data);
        } catch (Throwable ignored) {
        }
    }

    public void play(Location location) {
        for (String name : effects) {
            if (name.startsWith("SMOKE")) {
                name = trimSpace(name.substring(5));
                Integer data = null;
                if (name.length() >= 2) {
                    data = DIR_NAMES.get(name.substring(0, 2));
                }
                if (data == null && name.length() >= 1) {
                    data = DIR_NAMES.get(name.substring(0, 1));
                }
                if (data == null) {
                    try {
                        data = ParseUtil.parseInt(name, null);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (data == null) {
                    data = 0;
                }
                if (data == 4) {
                    play(location.clone().add(0.0, 0.5, 0.0), org.bukkit.Effect.SMOKE, data);
                } else {
                    play(location, org.bukkit.Effect.SMOKE, data);
                }
                continue;
            } else if (name.startsWith("RECORD")) {
                name = trimSpace(name.substring(6));
                if (name.startsWith("PLAY")) {
                    name = trimSpace(name.substring(4));
                }
                Integer data = DISK_NAMES.get(name);
                if (data == null) {
                    try {
                        data = ParseUtil.parseInt(name, null);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (data == null) {
                    data = 2257;
                }
                play(location, org.bukkit.Effect.RECORD_PLAY, data);
                continue;
            }
            org.bukkit.Effect effect = ParseUtil.parseEnum(org.bukkit.Effect.class, name, null);
            if (effect != null && !effect.toString().toUpperCase(Locale.ENGLISH).endsWith("_BREAK")) {
                play(location, effect, 0);
                continue;
            }
            Sound sound = ParseUtil.parseEnum(Sound.class, name, null);
            if (sound != null) {
                location.getWorld().playSound(location, sound, volume, pitch);
                continue;
            }
            location.getWorld().playSound(location, name, volume, pitch);
        }
    }

    public void play(Player player) {
        for (String name : effects) {
            player.playSound(player.getLocation(), name, volume, pitch);
        }
    }
}
