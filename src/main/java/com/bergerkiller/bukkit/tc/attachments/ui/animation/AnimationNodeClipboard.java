package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;

/**
 * Tracks the nodes stored on the clipboard by players.
 * Persistent between login/log-off. Not persistent on server
 * restarts.
 */
public class AnimationNodeClipboard {
    private static final HashMap<UUID, AnimationNodeClipboard> byPlayerUUID = new HashMap<>();
    private List<AnimationNode> nodes = Collections.emptyList();

    public static AnimationNodeClipboard of(Player player) {
        return byPlayerUUID.computeIfAbsent(player.getUniqueId(), u -> new AnimationNodeClipboard());
    }

    public static boolean hasClipboard(Player player) {
        AnimationNodeClipboard clipboard = byPlayerUUID.get(player.getUniqueId());
        return clipboard != null && !clipboard.nodes.isEmpty();
    }

    private AnimationNodeClipboard() {
    }

    public List<AnimationNode> contents() {
        return this.nodes;
    }

    public void store(List<AnimationNode> nodes) {
        // Create an immutable copy
        if (nodes.isEmpty()) {
            this.nodes = Collections.emptyList();
        } else {
            this.nodes = nodes.stream().map(AnimationNode::clone).collect(StreamUtil.toUnmodifiableList());
        }
    }
}
