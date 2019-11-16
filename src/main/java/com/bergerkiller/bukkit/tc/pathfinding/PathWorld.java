package com.bergerkiller.bukkit.tc.pathfinding;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.block.Block;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.collections.BlockMap;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * A single world on which path nodes are stored
 */
public class PathWorld {
    private final PathProvider _provider;
    private final String _name;
    private final BlockMap<PathNode> _blockNodes;
    private final Map<String, PathNode> _nodes;

    public PathWorld(PathProvider provider, String worldName) {
        _provider = provider;
        _name = worldName;
        _blockNodes = new BlockMap<>();
        _nodes = new HashMap<>();
    }

    /**
     * Gets the path provider responsible for this world
     * 
     * @return provider
     */
    public PathProvider getProvider() {
        return _provider;
    }

    /**
     * Gets the name of this World
     * 
     * @return world name
     */
    public String getName() {
        return _name;
    }

    public PathNode getNodeAtRail(BlockLocation railLocation) {
        return _blockNodes.get(railLocation);
    }

    public PathNode getNodeAtRail(Block railBlock) {
        return _blockNodes.get(railBlock);
    }

    public PathNode getNodeByName(String name) {
        return _nodes.get(name);
    }

    public Set<BlockLocation> getRailBlocks() {
        return _blockNodes.keySet();
    }

    public Collection<PathNode> getNodes() {
        return _nodes.values();
    }

    public PathNode removeAtRail(Block railBlock) {
        PathNode node = _blockNodes.remove(railBlock);
        if (node != null) node.remove();
        return node;
    }

    public PathNode getOrCreateAtRail(final String name, final BlockLocation location) {
        if (LogicUtil.nullOrEmpty(name) || location == null) {
            return null;
        }
        PathNode node = getNodeByName(name);
        if (node != null) {
            return node;
        }
        node = getNodeAtRail(location);
        if (node == null) {
            // Create a new node
            node = new PathNode(name, this, location);
            addToMapping(node);
            _provider.scheduleNode(node);
        } else {
            // Add the name to the existing node
            node.addName(name);
        }
        return node;
    }

    public PathNode addNode(final String name, final BlockLocation location) {
        PathNode node = new PathNode(name, this, location);
        addToMapping(node);
        return node;
    }

    public void rerouteAll() {
        for (BlockLocation location : getRailBlocks()) {
            _provider.discoverFromRail(location);
        }
        clearAll();
    }

    public void clearAll() {
        _nodes.clear();
        _blockNodes.clear();
        _provider.markChanged();
    }

    protected void addNodeName(PathNode node, String name) {
        if (!PathNode.SWITCHER_NAME_FALLBACK.equals(name)) {
            _nodes.put(name, node);
            _provider.markChanged();
        }
    }

    protected void removeNodeName(PathNode node, String name) {
        PathNode removed = _nodes.remove(name);
        if (removed == node) {
            _provider.markChanged();
        } else if (removed != null) {
            _nodes.put(name, removed); // restore
        }
    }

    protected void addToMapping(PathNode node) {
        for (String name : node.getNames()) {
            addNodeName(node, name);
        }
        _blockNodes.put(node.location, node);
        _provider.markChanged();
    }

    protected void removeFromMapping(PathNode node) {
        for (String name : node.getNames()) {
            PathNode removed = _nodes.remove(name);
            if (removed != null && removed != node) {
                _nodes.put(name, removed); // restore
            }
        }
        PathNode removed = _blockNodes.remove(node.location);
        if (removed != null && removed != node) {
            _blockNodes.put(node.location, removed); // restore
        }
        _provider.markChanged();
    }
}
