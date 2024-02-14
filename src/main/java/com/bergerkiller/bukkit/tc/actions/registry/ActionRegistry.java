package com.bergerkiller.bukkit.tc.actions.registry;

import com.bergerkiller.bukkit.common.utils.StreamUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.actions.*;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.components.ActionTracker;
import com.bergerkiller.bukkit.tc.controller.components.ActionTrackerMember;
import com.bergerkiller.bukkit.tc.offline.train.format.OfflineDataBlock;

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
        this.registerTrainCartsActions();
    }

    private void registerTrainCartsActions() {
        register(MemberActionLaunch.class, new MemberActionLaunch.Serializer());
        register(MemberActionLaunchDirection.class, new MemberActionLaunchDirection.Serializer());
        register(MemberActionLaunchLocation.class, new MemberActionLaunchLocation.Serializer());
        register(MemberActionWaitDistance.class, new MemberActionWaitDistance.Serializer());
        register(MemberActionWaitLocation.class, new MemberActionWaitLocation.Serializer());
        register(GroupActionWaitForever.class, new GroupActionWaitForever.Serializer());
        register(GroupActionWaitTill.class, new GroupActionWaitTill.Serializer());
        register(GroupActionWaitTicks.class, new GroupActionWaitTicks.Serializer());
        register(GroupActionWaitDelay.class, new GroupActionWaitDelay.Serializer());
        register(TrackedSignActionSetOutput.class, new TrackedSignActionSetOutput.Serializer(plugin));
        register(GroupActionSizzle.class, new GroupActionSizzle.Serializer());
    }

    /**
     * Registers a serializer for a certain action class. Only the exact action class type
     * can be loaded and saved. Derived classes are not included. Uses the full class name
     * as the identifier for the action.
     *
     * @param type Action Class
     * @param serializer Serializer
     * @param <T> Action Class type
     */
    public <T extends Action> void register(Class<T> type, Serializer<T> serializer) {
        register(type.getName(), type, serializer);
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
     * @see #saveAction(OfflineDataBlock, Action, ActionTracker)
     */
    public List<OfflineDataBlock> saveTracker(ActionTracker tracker) {
        if (tracker.hasAction()) {
            OfflineDataBlock root = OfflineDataBlock.create("root");
            for (Action action : tracker.getScheduledActions()) {
                saveAction(root, action, tracker);
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
    public void loadTracker(ActionTracker tracker, List<OfflineDataBlock> actionDataBlocks) {
        if (!actionDataBlocks.isEmpty()) {
            MinecartGroup group = tracker.getGroupOwner();
            for (OfflineDataBlock actionDataBlock : actionDataBlocks) {
                Action action = loadAction(group, actionDataBlock, tracker);
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
     * @param tracker Action tracker the action is part of
     * @return Added data block, or null if this action could not be saved
     */
    public OfflineDataBlock saveAction(OfflineDataBlock root, Action action, ActionTracker tracker) {
        final RegisteredAction registeredAction = byType.get(action.getClass());
        if (registeredAction == null) {
            return null;
        }

        boolean addedToMember = (tracker instanceof ActionTrackerMember);

        // Create child. Probably never fails.
        OfflineDataBlock child;
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
        boolean success = false;
        try {
            success = registeredAction.serializer.save(action, child, tracker);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save action " + action.getClass().getName(), t);
        }
        if (success) {
            return child;
        } else {
            root.children.remove(child);
            return null;
        }
    }

    /**
     * Loads an Action from a previously serialized Action OfflineDataBlock.
     * The action is not added to the member or group.
     *
     * @param group Group the action will be run in
     * @param dataBlock OfflineDataBlock to load
     * @param tracker Action tracker (group or member) the action will be added to after loading
     * @return Action that was loaded, or null if the data block could not be loaded
     */
    public Action loadAction(MinecartGroup group, OfflineDataBlock dataBlock, ActionTracker tracker) {
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

            Action action = registeredAction.serializer.load(dataBlock, tracker);
            if (action == null) {
                return null;
            }

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
         * @param data OfflineDataBlock
         * @param tracker The ActionTracker (group or member) where the action is saved of
         * @return True if saving was successful, False if the Action should be omitted because
         *         it could not be saved.
         * @throws IOException
         */
        boolean save(T action, OfflineDataBlock data, ActionTracker tracker) throws IOException;

        /**
         * Loads in this registered action. Child data blocks in the input data
         * can be used as metadata to load the action. Information such as the elapsed time
         * and the member that was set do not have to be loaded.
         *
         * @param data OfflineDataBlock
         * @param tracker The ActionTracker (group or member) where the action will be loaded into
         * @return Loaded Action, or null if the data could not be loaded
         * @throws IOException
         */
        T load(OfflineDataBlock data, ActionTracker tracker) throws IOException;
    }
}
