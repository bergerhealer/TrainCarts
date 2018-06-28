package com.bergerkiller.bukkit.tc.properties;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.Util;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberStore;
import com.bergerkiller.bukkit.tc.storage.OfflineGroupManager;
import com.bergerkiller.bukkit.tc.storage.OfflineMember;
import com.bergerkiller.bukkit.tc.utils.SignSkipOptions;
import com.bergerkiller.bukkit.tc.utils.SoftReference;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

public class CartProperties extends CartPropertiesStore implements IProperties {
    public static final CartProperties EMPTY = new CartProperties(UUID.randomUUID(), null);

    private final UUID uuid;
    private final Set<String> owners = new HashSet<>();
    private final Set<String> ownerPermissions = new HashSet<>();
    private final Set<String> tags = new HashSet<>();
    private final Set<Material> blockBreakTypes = new HashSet<>();
    public Vector exitOffset = new Vector(0.0, 0.0, 0.0);
    public float exitYaw = 0.0f, exitPitch = 0.0f;
    protected TrainProperties group = null;
    private boolean allowPlayerExit = true;
    private boolean allowPlayerEnter = true;
    private boolean invincible = false;
    private String enterMessage = null;
    private String destination = "";
    private String lastPathNode = "";
    private boolean isPublic = true;
    private boolean pickUp = false;
    private boolean spawnItemDrops = true;
    private SoftReference<MinecartMember<?>> member = new SoftReference<>();
    private SignSkipOptions skipOptions = new SignSkipOptions();
    private AttachmentModel model = null;
    private String driveSound = "";

    protected CartProperties(UUID uuid, TrainProperties group) {
        this.uuid = uuid;
        this.group = group;
    }

    public static boolean hasGlobalOwnership(Player player) {
        return Permission.COMMAND_GLOBALPROPERTIES.has(player);
    }

    public TrainProperties getTrainProperties() {
        return this.group;
    }

    @Override
    public String getTypeName() {
        return "cart";
    }

    /**
     * Sets the holder of these properties. Internal use only.
     * 
     * @param holder
     */
    protected void setHolder(MinecartMember<?> holder) {
        this.member.set(holder);
    }

    @Override
    public MinecartMember<?> getHolder() {
        MinecartMember<?> member = this.member.get();
        if (member == null || member.getEntity() == null || !member.getEntity().getUniqueId().equals(this.uuid)) {
            return this.member.set(MinecartMemberStore.getFromUID(this.uuid));
        } else {
            return member;
        }
    }

    @Override
    public boolean hasHolder() {
        return getHolder() != null;
    }

    @Override
    public boolean restore() {
        return getTrainProperties().restore() && hasHolder();
    }

    public MinecartGroup getGroup() {
        MinecartMember<?> member = this.getHolder();
        if (member == null) {
            return this.group == null ? null : this.group.getHolder();
        } else {
            return member.getGroup();
        }
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public void tryUpdate() {
        MinecartMember<?> m = this.getHolder();
        if (m != null) m.onPropertiesChanged();
    }

    /**
     * Gets a collection of player UUIDs that are editing these properties
     *
     * @return Collection of editing player UUIDs
     */
    public Collection<UUID> getEditing() {
        ArrayList<UUID> players = new ArrayList<>();
        for (Map.Entry<UUID, CartProperties> entry : editing.entrySet()) {
            if (entry.getValue() == this) {
                players.add(entry.getKey());
            }
        }
        return players;
    }

    /**
     * Gets a collection of online players that are editing these properties
     *
     * @return Collection of editing players
     */
    public Collection<Player> getEditingPlayers() {
        Collection<UUID> uuids = getEditing();
        ArrayList<Player> players = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            Player p = Bukkit.getServer().getPlayer(uuid);
            if (p != null) {
                players.add(p);
            }
        }
        return players;
    }

    /*
     * Block obtaining
     */
    public boolean canBreak(Block block) {
        return !this.blockBreakTypes.isEmpty() && this.blockBreakTypes.contains(block.getType());
    }

    /*
     * Owners
     */
    @Override
    public boolean hasOwnership(Player player) {
        if (hasGlobalOwnership(player) || this.isOwnedByEveryone() || this.isOwner(player)) {
            return true;
        }
        for (String ownerPermission : this.getOwnerPermissions()) {
            if (CommonUtil.hasPermission(player, ownerPermission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isOwner(Player player) {
        return this.isOwner(player.getName().toLowerCase());
    }

    public boolean isOwner(String player) {
        return this.owners.contains(player.toLowerCase());
    }

    public void setOwner(String player) {
        this.setOwner(player, true);
    }

    public void setOwner(String player, boolean owner) {
        if (owner) {
            this.owners.add(player);
        } else {
            this.owners.remove(player);
        }
    }

    public void setOwner(Player player) {
        this.setOwner(player, true);
    }

    public void setOwner(Player player, boolean owner) {
        if (player == null) {
            return;
        }
        this.setOwner(player.getName().toLowerCase(), owner);
    }

    @Override
    public boolean isOwnedByEveryone() {
        return !this.hasOwners() && !this.hasOwnerPermissions();
    }

    @Override
    public Set<String> getOwnerPermissions() {
        return this.ownerPermissions;
    }

    @Override
    public void clearOwnerPermissions() {
        this.ownerPermissions.clear();
    }

    @Override
    public boolean hasOwnerPermissions() {
        return !this.ownerPermissions.isEmpty();
    }

    @Override
    public Set<String> getOwners() {
        return this.owners;
    }

    @Override
    public void clearOwners() {
        this.owners.clear();
    }

    @Override
    public boolean hasOwners() {
        return !this.owners.isEmpty();
    }

    public boolean sharesOwner(CartProperties properties) {
        if (!this.hasOwners()) return true;
        if (!properties.hasOwners()) return true;
        for (String owner : properties.owners) {
            if (properties.isOwner(owner)) return true;
        }
        return false;
    }

    /**
     * Gets whether this Minecart can pick up nearby items
     *
     * @return True if it can pick up items, False if not
     */
    public boolean canPickup() {
        return this.pickUp;
    }

    public void setPickup(boolean pickup) {
        this.pickUp = pickup;
    }

    @Override
    public boolean isPublic() {
        return this.isPublic;
    }

    @Override
    public void setPublic(boolean state) {
        this.isPublic = state;
    }

    @Override
    public boolean matchTag(String tag) {
        return Util.matchText(this.tags, tag);
    }

    @Override
    public boolean hasTags() {
        return !this.tags.isEmpty();
    }

    @Override
    public void clearTags() {
        this.tags.clear();
    }

    @Override
    public void addTags(String... tags) {
        Collections.addAll(this.tags, tags);
    }

    @Override
    public void removeTags(String... tags) {
        for (String tag : tags) {
            this.tags.remove(tag);
        }
    }

    @Override
    public Set<String> getTags() {
        return this.tags;
    }

    @Override
    public void setTags(String... tags) {
        this.tags.clear();
        this.addTags(tags);
    }

    @Override
    public boolean getSpawnItemDrops() {
        return this.spawnItemDrops;
    }

    @Override
    public void setSpawnItemDrops(boolean spawnDrops) {
        this.spawnItemDrops = spawnDrops;
    }

    @Override
    public BlockLocation getLocation() {
        MinecartMember<?> member = this.getHolder();
        if (member != null) {
            return new BlockLocation(member.getEntity().getLocation().getBlock());
        } else {
            // Offline member?
            OfflineMember omember = OfflineGroupManager.findMember(this.getTrainProperties().getTrainName(), this.getUUID());
            if (omember == null) {
                return null;
            } else {
                // Find world
                World world = Bukkit.getWorld(omember.group.worldUUID);
                if (world == null) {
                    return new BlockLocation("Unknown", omember.cx << 4, 0, omember.cz << 4);
                } else {
                    return new BlockLocation(world, omember.cx << 4, 0, omember.cz << 4);
                }
            }
        }
    }

    /**
     * Tests whether the Minecart has block types it can break
     *
     * @return True if materials are contained, False if not
     */
    public boolean hasBlockBreakTypes() {
        return !this.blockBreakTypes.isEmpty();
    }

    /**
     * Clears all the materials this Minecart can break
     */
    public void clearBlockBreakTypes() {
        this.blockBreakTypes.clear();
    }

    /**
     * Gets a Collection of materials this Minecart can break
     *
     * @return a Collection of blocks that are broken
     */
    public Collection<Material> getBlockBreakTypes() {
        return this.blockBreakTypes;
    }

    /**
     * Gets the Enter message that is currently displayed when a player enters
     *
     * @return Enter message
     */
    public String getEnterMessage() {
        return this.enterMessage;
    }

    @Override
    public void setEnterMessage(String message) {
        this.enterMessage = message;
    }

    /**
     * Gets whether an Enter message is set
     *
     * @return True if a message is set, False if not
     */
    public boolean hasEnterMessage() {
        return this.enterMessage != null && !this.enterMessage.equals("");
    }

    /**
     * Shows the enter message to the player specified
     *
     * @param player to display the message to
     */
    public void showEnterMessage(Player player) {
        if (this.hasEnterMessage()) {
            TrainCarts.sendMessage(player, ChatColor.YELLOW + TrainCarts.getMessage(enterMessage));
        }
    }

    public void clearDestination() {
        this.destination = "";
    }

    @Override
    public boolean hasDestination() {
        return this.destination.length() != 0;
    }

    @Override
    public String getDestination() {
        return this.destination;
    }

    @Override
    public void setDestination(String destination) {
        this.destination = destination == null ? "" : destination;
    }

    @Override
    public String getLastPathNode() {
        return this.lastPathNode;
    }

    @Override
    public void setLastPathNode(String nodeName) {
        this.lastPathNode = nodeName;
    }

    /**
     * Gets the attachment model set for this particular cart. If no model was previously set,
     * a model is created based on the vanilla default model that is used. This model is not saved
     * unless additional changes are made to it.
     * 
     * @return model set, null for Vanilla
     */
    public AttachmentModel getModel() {
        if (this.model == null) {
            // No model was set. Create a Vanilla model based on the Minecart information
            MinecartMember<?> member = this.getHolder();
            EntityType minecartType = (member == null) ? EntityType.MINECART : member.getEntity().getType();
            this.model = AttachmentModel.getDefaultModel(minecartType);
        }
        return this.model;
    }

    /**
     * Resets any set model, restoring the Minecart to its Vanilla defaults.
     */
    public void resetModel() {
        if (this.model != null) {
            MinecartMember<?> member = this.getHolder();
            this.model.resetToDefaults((member == null) ? EntityType.MINECART : member.getEntity().getType());
        }
    }

    /**
     * Sets the attachment model to a named model from the attachment model store.
     * The model will be stored as a named link when saved/reloaded.
     * Calling this method will remove any model set for this minecart.
     * 
     * @param modelName
     */
    public void setModelName(String modelName) {
        if (this.model == null) {
            this.model = new AttachmentModel();
        }
        this.model.resetToName(modelName);
    }

    @Override
    public boolean parseSet(String key, String arg) {
        TrainPropertiesStore.markForAutosave();
        if (key.equals("exitoffset")) {
            Vector vec = Util.parseVector(arg, null);
            if (vec != null) {
                if (vec.length() > TCConfig.maxEjectDistance) {
                    vec.normalize().multiply(TCConfig.maxEjectDistance);
                }
                exitOffset = vec;
            }
        } else if (key.equals("exityaw")) {
            exitYaw = ParseUtil.parseFloat(arg, 0.0f);
        } else if (key.equals("exitpitch")) {
            exitPitch = ParseUtil.parseFloat(arg, 0.0f);
        } else if (LogicUtil.contains(key, "exitrot", "exitrotation")) {
            String[] angletext = Util.splitBySeparator(arg);
            float yaw = 0.0f;
            float pitch = 0.0f;
            if (angletext.length == 2) {
                yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
                pitch = ParseUtil.parseFloat(angletext[1], 0.0f);
            } else if (angletext.length == 1) {
                yaw = ParseUtil.parseFloat(angletext[0], 0.0f);
            }
            exitYaw = yaw;
            exitPitch = pitch;
        } else if (key.equals("addtag")) {
            this.addTags(arg);
        } else if (key.equals("settag")) {
            this.setTags(arg);
        } else if (key.equals("destination")) {
            this.setDestination(arg);
        } else if (key.equals("remtag") || key.equals("removetag")) {
            this.removeTags(arg);
        } else if (key.equals("playerenter")) {
            this.setPlayersEnter(ParseUtil.parseBool(arg));
        } else if (key.equals("playerexit")) {
            this.setPlayersExit(ParseUtil.parseBool(arg));
        } else if (LogicUtil.contains(key, "invincible", "godmode")) {
            this.setInvincible(ParseUtil.parseBool(arg));
        } else if (key.equals("setownerperm")) {
            this.clearOwnerPermissions();
            this.getOwnerPermissions().add(arg);
        } else if (key.equals("addownerperm")) {
            this.getOwnerPermissions().add(arg);
        } else if (key.equals("remownerperm")) {
            this.getOwnerPermissions().remove(arg);
        } else if (key.equals("setowner")) {
            arg = arg.toLowerCase();
            this.setOwner(arg);
        } else if (key.equals("addowner")) {
            arg = arg.toLowerCase();
            this.getOwners().add(arg);
        } else if (key.equals("remowner")) {
            arg = arg.toLowerCase();
            this.getOwners().remove(arg);
        } else if (key.equals("model")) {
            setModelName(arg);
        } else if (key.equals("clearmodel") || key.equals("resetmodel")) {
            resetModel();
        } else if (LogicUtil.contains(key, "spawnitemdrops", "spawndrops", "killdrops")) {
            this.setSpawnItemDrops(ParseUtil.parseBool(arg));
        } else if(key.equals("drivesound")) {
            this.setDriveSound(arg);
        } else {
            return false;
        }
        this.tryUpdate();
        return true;
    }

    /**
     * Loads the information from the properties specified
     *
     * @param from to load from
     */
    public void load(CartProperties from) {
        this.destination = from.destination;
        this.owners.clear();
        this.owners.addAll(from.owners);
        this.ownerPermissions.clear();
        this.ownerPermissions.addAll(from.ownerPermissions);
        this.tags.clear();
        this.tags.addAll(from.tags);
        this.allowPlayerEnter = from.allowPlayerEnter;
        this.allowPlayerExit = from.allowPlayerExit;
        this.invincible = from.invincible;
        this.exitOffset = from.exitOffset.clone();
        this.exitYaw = from.exitYaw;
        this.exitPitch = from.exitPitch;
        this.spawnItemDrops = from.spawnItemDrops;
        this.driveSound = from.driveSound;
    }

    @Override
    public void load(ConfigurationNode node) {
        for (String owner : node.getList("owners", String.class)) {
            this.owners.add(owner.toLowerCase());
        }
        this.ownerPermissions.addAll(node.getList("ownerPermissions", String.class));
        for (String tag : node.getList("tags", String.class)) {
            this.tags.add(tag);
        }
        this.destination = node.get("destination", this.destination);
        this.lastPathNode = node.get("lastPathNode", this.lastPathNode);
        this.allowPlayerEnter = node.get("allowPlayerEnter", this.allowPlayerEnter);
        this.allowPlayerExit = node.get("allowPlayerExit", this.allowPlayerExit);
        this.invincible = node.get("invincible", this.invincible);
        this.isPublic = node.get("isPublic", this.isPublic);
        this.pickUp = node.get("pickUp", this.pickUp);
        this.spawnItemDrops = node.get("spawnItemDrops", this.spawnItemDrops);
        this.exitOffset = node.get("exitOffset", this.exitOffset);
        this.exitYaw = node.get("exitYaw", this.exitYaw);
        this.exitPitch = node.get("exitPitch", this.exitPitch);
        this.driveSound = node.get("driveSound", this.driveSound);
        for (String blocktype : node.getList("blockBreakTypes", String.class)) {
            Material mat = ParseUtil.parseMaterial(blocktype, null);
            if (mat != null) {
                this.blockBreakTypes.add(mat);
            }
        }
        if (node.isNode("skipOptions")) {
            this.skipOptions.load(node.getNode("skipOptions"));
        }
        if (this.model != null) {
            if (node.isNode("model")) {
                this.model.update(node.getNode("model").clone());
            }
        } else {
            if (node.isNode("model")) {
                this.model = new AttachmentModel(node.getNode("model").clone());
            } else {
                this.model = null;
            }
        }
    }

    @Override
    public void saveAsDefault(ConfigurationNode node) {
        node.set("owners", new ArrayList<>(this.owners));
        node.set("ownerPermissions", new ArrayList<>(this.ownerPermissions));
        node.set("tags", new ArrayList<>(this.tags));
        node.set("allowPlayerEnter", this.allowPlayerEnter);
        node.set("allowPlayerExit", this.allowPlayerExit);
        node.set("invincible", this.invincible);
        node.set("isPublic", this.isPublic);
        node.set("pickUp", this.pickUp);
        node.set("exitOffset", this.exitOffset);
        node.set("exitYaw", this.exitYaw);
        node.set("exitPitch", this.exitPitch);
        node.set("driveSound", this.driveSound);
        List<String> items = node.getList("blockBreakTypes", String.class);
        for (Material mat : this.blockBreakTypes) {
            items.add(mat.toString());
        }
        node.set("destination", this.hasDestination() ? this.destination : "");
        node.set("enterMessage", this.hasEnterMessage() ? this.enterMessage : "");
        node.set("spawnItemDrops", this.spawnItemDrops);

        if (this.model != null && !this.model.isDefault()) {
            node.set("model", this.model.getConfig());
        } else {
            node.remove("model");
        }
    }

    @Override
    public void save(ConfigurationNode node) {
        node.set("owners", this.owners.isEmpty() ? null : new ArrayList<>(this.owners));
        node.set("ownerPermissions", this.ownerPermissions.isEmpty() ? null : new ArrayList<>(this.ownerPermissions));
        node.set("tags", this.tags.isEmpty() ? null : new ArrayList<>(this.tags));
        node.set("allowPlayerEnter", this.allowPlayerEnter ? null : false);
        node.set("allowPlayerExit", this.allowPlayerExit ? null : false);
        node.set("invincible", this.invincible ? true : null);
        node.set("isPublic", this.isPublic ? null : false);
        node.set("pickUp", this.pickUp ? true : null);
        node.set("exitOffset", this.exitOffset.lengthSquared() == 0.0 ? null : this.exitOffset);
        node.set("exitYaw", this.exitYaw == 0.0f ? null : this.exitYaw);
        node.set("exitPitch", this.exitPitch == 0.0f ? null : this.exitPitch);
        node.set("driveSound", this.driveSound == "" ? null : this.driveSound);
        if (this.blockBreakTypes.isEmpty()) {
            node.remove("blockBreakTypes");
        } else {
            List<String> items = node.getList("blockBreakTypes", String.class);
            for (Material mat : this.blockBreakTypes) {
                items.add(mat.toString());
            }
        }
        node.set("destination", this.hasDestination() ? this.destination : null);
        node.set("lastPathNode", LogicUtil.nullOrEmpty(this.lastPathNode) ? null : this.lastPathNode);
        node.set("enterMessage", this.hasEnterMessage() ? this.enterMessage : null);
        node.set("spawnItemDrops", this.spawnItemDrops ? null : false);

        if (this.skipOptions.isActive()) {
            this.skipOptions.save(node.getNode("skipOptions"));
        } else if (node.contains("skipOptions")) {
            node.remove("skipOptions");
        }

        if (this.model != null) {
            node.set("model", this.model.getConfig());
        } else {
            node.remove("model");
        }
    }

    /**
     * Gets wether this Train is invincible or not
     *
     * @return True if enabled, False if not
     */
    public boolean isInvincible() {
        return this.invincible;
    }

    /**
     * Sets wether this Train can be damages
     *
     * @param enabled state to set to
     */
    public void setInvincible(boolean enabled) {
        this.invincible = enabled;
    }

    @Override
    public boolean getPlayersEnter() {
        return this.allowPlayerEnter;
    }

    @Override
    public void setPlayersEnter(boolean state) {
        this.allowPlayerEnter = state;
    }

    @Override
    public boolean getPlayersExit() {
        return this.allowPlayerExit;
    }

    @Override
    public void setPlayersExit(boolean state) {
        this.allowPlayerExit = state;
    }

    public SignSkipOptions getSkipOptions() {
        return this.skipOptions;
    }

    public void setSkipOptions(SignSkipOptions options) {
        this.skipOptions.filter = options.filter;
        this.skipOptions.ignoreCtr = options.ignoreCtr;
        this.skipOptions.skipCtr = options.skipCtr;
    }

    public String getDriveSound() {
        return driveSound;
    }

    public void setDriveSound(String driveSound) {
        this.driveSound = driveSound;
    }
}
