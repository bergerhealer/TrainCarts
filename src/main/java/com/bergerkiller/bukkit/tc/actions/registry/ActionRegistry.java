package com.bergerkiller.bukkit.tc.actions.registry;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.Action;
import com.bergerkiller.bukkit.tc.actions.MemberAction;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.ActionTracker;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerGroup;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerMember;
import com.bergerkiller.bukkit.tc.offline.train.format.DataBlock;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Level;

/**
 * Action registry is used to make actions scheduled for groups and/or members
 * persistent. Only actions registered here can survive server restarts and
 * train unload-reload events.
 */
public class ActionRegistry {
    private final TrainCarts plugin;
    private final Map<String, RegisteredAction> byId = new HashMap<>();
    private final WeakHashMap<Class<?>, RegisteredAction> byType = new WeakHashMap<>();

    public ActionRegistry(TrainCarts plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a serializer for a certain action class. Only the exact action class type
     * can be loaded and saved. Derived classes are not included.
     *
     * @param id Unique identifier of the action
     * @param type Action Class
     * @param serializer Serializer
     * @param <T> Action Class type
     */
    public <T extends Action> void register(String id, Class<T> type, Serializer<T> serializer) {
        RegisteredAction registered = new RegisteredAction(id, type, serializer);
        byId.put(id, registered);
        byType.put(type, registered);
    }

    /**
     * Un-registers a previously registered Action
     *
     * @param id Unique identifier of the action
     */
    public void unregister(String id) {
        byId.remove(id);
        // Note: not removed from byType, because it might still be needed for
        //       saving actions that were still added to trains
        //       It's a weak hash map so if that's not the case, it'll unload
        //       automatically.
    }

    /**
     * Saves all actions scheduled in an action tracker into serialized data blocks.
     * The returned list can then be saved for later re-loading.
     *
     * @param tracker Action Tracker containing actions
     * @return List of data blocks to represent the actions
     * @see #saveAction(DataBlock, Action, boolean)
     */
    public List<DataBlock> saveTracker(ActionTracker tracker) {
        if (tracker.hasAction()) {
            DataBlock root = DataBlock.create("root");
            boolean addedToMember = (tracker instanceof ActionTrackerMember);
            for (Action action : tracker.getScheduledActions()) {
                saveAction(root, action, addedToMember);
            }
            return Collections.unmodifiableList(root.children);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Loads all the actions from the data blocks and adds them to an action tracker
     *
     * @param tracker Action Tracker to load
     * @param actionDataBlocks Previously saved data blocks for all actions
     */
    public void loadTracker(ActionTracker tracker, List<DataBlock> actionDataBlocks) {
        if (!actionDataBlocks.isEmpty()) {
            MinecartGroup group;
            if (tracker instanceof ActionTrackerGroup) {
                group = ((ActionTrackerGroup) tracker).getOwner();
            } else if (tracker instanceof ActionTrackerMember) {
                group = ((ActionTrackerMember) tracker).getOwner().getGroup();
            } else {
                throw new IllegalArgumentException("Action tracker is invalid type: " + tracker.getClass().getName());
            }

            for (DataBlock actionDataBlock : actionDataBlocks) {
                Action action = loadAction(group, actionDataBlock);
                if (action != null) {
                    tracker.addAction(action);
                }
            }
        }
    }

    /**
     * Saves an Action and writes the saved data as a new child of the
     * root data block.
     *
     * @param root Root data block to which this action will be added as child
     * @param action Action to save
     * @param addedToMember True if the action was added to a member. False if it was
     *                      added to a Group instead.
     * @return Added data block, or null if this action could not be saved
     */
    public DataBlock saveAction(DataBlock root, Action action, boolean addedToMember) {
        final RegisteredAction registeredAction = byType.get(action.getClass());
        if (registeredAction == null) {
            return null;
        }

        // Create child. Probably never fails.
        DataBlock child;
        try {
            child = root.addChild("action", stream -> {
                stream.writeUTF(registeredAction.id);
                int elapsedTicks = action.elapsedTicks();
                stream.writeInt(elapsedTicks);
                if (elapsedTicks > 0) {
                    stream.writeLong(action.elapsedTimeMillis());
                }
                Set<String> tags = action.getTags();
                Util.writeVariableLengthInt(stream, tags.size());
                for (String tag : tags) {
                    stream.writeUTF(tag);
                }

                MinecartMember<?> member;
                if (!addedToMember && action instanceof MemberAction && (member = ((MemberAction) action).getMember()) != null) {
                    stream.writeBoolean(true);
                    StreamUtil.writeUUID(stream, member.getEntity().getUniqueId());
                } else {
                    stream.writeBoolean(false);
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save action " + action.getClass().getName(), t);
            return null;
        }

        // Ask serializer to save the action. Could fail, in which case we remove the action data block
        // again to avoid trouble.
        try {
            registeredAction.serializer.save(action, child);
        } catch (Throwable t) {
            root.children.remove(child);
            plugin.getLogger().log(Level.SEVERE, "Failed to save action " + action.getClass().getName(), t);
            return null;
        }

        return child;
    }

    /**
     * Loads an Action from a previously serialized Action DataBlock.
     * The action is not added to the member or group.
     *
     * @param group Group the action will be run in
     * @param dataBlock DataBlock to load
     * @return Action that was loaded, or null if the data block could not be loaded
     */
    public Action loadAction(MinecartGroup group, DataBlock dataBlock) {
        // Name identifier for the action
        RegisteredAction registeredAction = null;
        // For MemberAction that is added to a group, includes the member argument
        // Not needed otherwise (it's always the group or member the action is added to)
        final MinecartMember<?> member;
        // The number of ticks the action has run. If this number is 0,
        // then the action hasn't been started yet
        final int elapsedTicks;
        // The amount of time the action has run, in milliseconds. If this number is 0,
        // then the action hasn't been started yet
        final long elapsedTimeMillis;
        // String tags assigned to the action
        final List<String> tags;

        // Load the contents of the data block itself, which is details common to all
        // actions.
        try (DataInputStream stream = dataBlock.readData()) {
            registeredAction = byId.get(stream.readUTF());
            if (registeredAction == null) {
                return null; // Action no longer exists and cannot be loaded in
            }

            elapsedTicks = stream.readInt();
            elapsedTimeMillis = (elapsedTicks > 0) ? stream.readLong() : 0L;
            int numTags = Util.readVariableLengthInt(stream);
            if (numTags > 0) {
                tags = new ArrayList<>(numTags);
                for (int i = 0; i < numTags; i++) {
                    tags.add(stream.readUTF());
                }
            } else {
                tags = Collections.emptyList();
            }
            if (stream.readBoolean()) {
                UUID memberUUID = StreamUtil.readUUID(stream);
                MinecartMember<?> memberFound = null;
                for (MinecartMember<?> groupMember : group) {
                    if (groupMember.getEntity().getUniqueId().equals(memberUUID)) {
                        memberFound = groupMember;
                        break;
                    }
                }
                if (memberFound == null) {
                    return null; // Member the action was for no longer exists?
                }
                member = memberFound;
            } else {
                member = null;
            }

            Action action = registeredAction.serializer.load(dataBlock);
            Action.loadElapsedTime(action, elapsedTicks, elapsedTimeMillis);
            for (String tag : tags) {
                action.addTag(tag);
            }
            if (member != null && action instanceof MemberAction) {
                ((MemberAction) action).setMember(member);
            }
            return action;
        } catch (Throwable t) {
            if (registeredAction != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load action " + registeredAction.type.getName(), t);
            } else {
                plugin.getLogger().log(Level.SEVERE, "Failed to load corrupted action", t);
            }
            return null;
        }
    }

    private static final class RegisteredAction {
        public final String id;
        public final Class<?> type;
        public final Serializer<Action> serializer;

        @SuppressWarnings("unchecked")
        public <T extends Action> RegisteredAction(String id, Class<T> type, Serializer<T> serializer) {
            this.id = id;
            this.type = type;
            this.serializer = (Serializer<Action>) serializer;
        }
    }

    /**
     * Serializes an Action to data, or de-serializes an action from data
     *
     * @param <T> Action Type
     */
    public interface Serializer<T extends Action> {
        /**
         * Saves an action to a data block. If this action contains metadata that must
         * be saved to child data blocks, this method should do so.
         *
         * @param action Action to save
         * @param data DataBlock
         * @throws IOException
         */
        void save(T action, DataBlock data) throws IOException;

        /**
         * Loads in this registered action. Child data blocks in the input data
         * can be used as metadata to load the action. Information such as the elapsed time
         * and the member that was set do not have to be loaded.
         *
         * @param data DataBlock
         * @return Loaded Action, or null if the data could not be loaded
         * @throws IOException
         */
        T load(DataBlock data) throws IOException;
    }
}
