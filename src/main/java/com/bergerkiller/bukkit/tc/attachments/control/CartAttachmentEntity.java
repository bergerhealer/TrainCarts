package com.bergerkiller.bukkit.tc.attachments.control;

import static com.bergerkiller.bukkit.common.utils.MaterialUtil.getMaterial;

import com.bergerkiller.bukkit.common.Common;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.entity.CommonEntity;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.wrappers.BoatWoodType;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetToggleButton;
import com.bergerkiller.bukkit.tc.attachments.ui.entity.MapWidgetEntityTypeList;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityHandle;
import com.bergerkiller.generated.net.minecraft.network.protocol.game.PacketPlayOutSpawnEntityLivingHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.decoration.EntityArmorStandHandle;
import com.bergerkiller.generated.net.minecraft.world.entity.vehicle.EntityBoatHandle;

/**
 * A cart attachment that is a standard Entity.
 * This is also used for Vanilla style minecarts.
 */
public class CartAttachmentEntity extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "ENTITY";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            EntityType type = config.get("entityType", EntityType.MINECART);
            if (type == EntityType.BOAT) {
                BoatWoodType boatWoodType = config.contains("boatWoodType") ? config.get("boatWoodType", BoatWoodType.OAK) : BoatWoodType.OAK;
                Material itemMaterial;
                if (boatWoodType == BoatWoodType.OAK) {
                    itemMaterial = getMaterial("LEGACY_BOAT");
                } else {
                    itemMaterial = getMaterial("LEGACY_BOAT_" + boatWoodType.name());
                    if (itemMaterial == null) {
                        itemMaterial = getMaterial("LEGACY_BOAT");
                    }
                }
                return TCConfig.resourcePack.getItemTexture(new ItemStack(itemMaterial), 16, 16);
            } else if (type == EntityType.MINECART) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_CHEST) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_STORAGE_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_COMMAND) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_COMMAND_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_FURNACE) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_POWERED_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_HOPPER) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_HOPPER_MINECART")), 16, 16);
            } else if (type == EntityType.MINECART_MOB_SPAWNER) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_MOB_SPAWNER")), 16, 16);
            } else if (type == EntityType.MINECART_TNT) {
                return TCConfig.resourcePack.getItemTexture(new ItemStack(getMaterial("LEGACY_EXPLOSIVE_MINECART")), 16, 16);
            } else {
                return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/mob.png");
            }
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentEntity();
        }

        @Override
        public void getDefaultConfig(ConfigurationNode config) {
            config.set("entityType", EntityType.MINECART);
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            // For boats: wood type selector
            final MapWidget boatTypeSelector = tab.addWidget(new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    for (BoatWoodType type : BoatWoodType.values()) {
                        this.addItem(type.name());
                    }
                    if (attachment.getConfig().contains("boatWoodType")) {
                        this.setSelectedItem(attachment.getConfig().get("boatWoodType", "OAK"));
                    } else {
                        this.setSelectedItem("OAK");
                    }
                }

                @Override
                public void onSelectedItemChanged() {
                    if (this.isVisible()) {
                        attachment.getConfig().set("boatWoodType", this.getSelectedItem());
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                        attachment.resetIcon();
                    }
                }
            }).setBounds(0, 15, 100, 12).setVisible(false);

            tab.addWidget(new MapWidgetEntityTypeList() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.setEntityType(attachment.getConfig().get("entityType", EntityType.MINECART));
                    boatTypeSelector.setVisible(this.getEntityType() == EntityType.BOAT);
                }

                @Override
                public void onEntityTypeChanged() {
                    attachment.getConfig().set("entityType", this.getEntityType());
                    boatTypeSelector.setVisible(this.getEntityType() == EntityType.BOAT);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                }
            }).setBounds(0, 1, 100, 12);

            tab.addWidget(new MapWidgetToggleButton<Boolean>() {
                @Override
                public void onSelectionChanged() {
                    attachment.getConfig().set("sitting", this.getSelectedOption());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                    display.playSound(SoundEffect.CLICK);
                }
            }).addOptions(b -> "Sitting: " + (b ? "YES" : "NO"), Boolean.TRUE, Boolean.FALSE)
              .setSelectedOption(attachment.getConfig().getOrDefault("sitting", false))
              .setBounds(0, 56, 102, 12);

            tab.addWidget(new MapWidgetButton() {
                private void refreshText() {
                    if (attachment.getConfig().contains("nametag")) {
                        ConfigurationNode nametag = attachment.getConfig().getNode("nametag");
                        if (nametag.get("used", true)) {
                            if (nametag.get("visible", true)) {
                                setText("Nametag (vis.)");
                            } else {
                                setText("Nametag (invis.)");
                            }
                            return;
                        }
                    }
                    setText("No Nametag");
                }

                @Override
                public void onAttached() {
                    refreshText();
                }

                @Override
                public void onActivate() {
                    ConfigurationNode nametag = attachment.getConfig().getNode("nametag");
                    if (nametag.get("used", true)) {
                        if (nametag.get("visible", true)) {
                            nametag.set("visible", false);
                        } else {
                            nametag.set("used", false);
                        }
                    } else {
                        nametag.set("used", true);
                        nametag.set("visible", true);
                        if (!nametag.contains("text")) {
                            nametag.set("text", "Nametag");
                        }
                    }
                    refreshText();
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setBounds(0, 69, 79, 12);

            final MapWidgetSubmitText nameTagTextBox = tab.addWidget(new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    this.setDescription("Enter nametag title");
                }

                @Override
                public void onAccept(String text) {
                    ConfigurationNode nametag = attachment.getConfig().getNode("nametag");
                    nametag.set("used", true);
                    nametag.set("text", text);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            });
            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    nameTagTextBox.activate();
                }
            }).setText("Edit").setBounds(80, 69, 22, 12);
        }
    };

    private VirtualEntity actual;
    private VirtualEntity entity;

    private VirtualEntity actualEntity() {
        return actual != null ? actual : entity;
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.entity = null;
        this.actual = null;
    }

    @Override
    public boolean checkCanReload(ConfigurationNode config) {
        if (!super.checkCanReload(config)) {
            return false;
        }

        VirtualEntity displayed = actualEntity();

        // Change in entity type requires re-creating
        EntityType entityType = config.getOrDefault("entityType", EntityType.MINECART);
        if (displayed.getEntityType() != entityType) {
            return false;
        }

        // Change of sitting requires respawning the mount (only check for non-shulker)
        boolean currSitting = (actual != null);
        boolean newSitting = config.getOrDefault("sitting", false);
        if (newSitting != currSitting && !entityType.name().equals("SHULKER")) {
            return false;
        }

        return true;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        EntityType entityType = this.getConfig().getOrDefault("entityType", EntityType.MINECART);
        boolean sitting = this.getConfig().getOrDefault("sitting", false);

        // Some entity types cannot be spawned, use placeholder
        if (!isEntityTypeSupported(entityType)) {
            entityType = EntityType.MINECART;
        }

        if (this.getParent() != null || !VirtualEntity.isMinecart(entityType) || !hasController()) {
            // Generate entity (UU)ID
            this.entity = new VirtualEntity(this.getManager());
        } else {
            // Root Minecart node - allow the same Entity Id as the minecart to be used
            CommonEntity<?> entity = this.getController().getMember().getEntity();
            this.entity = new VirtualEntity(this.getManager(), entity.getEntityId(), entity.getUniqueId());
            this.entity.setUseParentMetadata(true);
            this.entity.setRespawnOnPitchFlip(true);
        }
        this.entity.setEntityType(entityType);

        // Minecarts have a 'strange' rotation point - fix it!
        if (this.entity.isMinecart() && !this.entity.isExperimentalMinecart()) {
            final double MINECART_CENTER_Y = 0.3765;
            this.entity.setPosition(new Vector(0.0, MINECART_CENTER_Y, 0.0));
            this.entity.setRelativeOffset(0.0, -MINECART_CENTER_Y, 0.0);
        }

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        if (sitting || entityType.name().equals("SHULKER")) {
            // Mount inside a marker armorstand
            this.actual = this.entity;
            this.entity = new VirtualEntity(this.getManager());
            this.entity.setEntityType(EntityType.ARMOR_STAND);
            this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
            this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
            this.entity.getMetaData().set(EntityArmorStandHandle.DATA_ARMORSTAND_FLAGS, (byte) (
                    EntityArmorStandHandle.DATA_FLAG_SET_MARKER |
                    EntityArmorStandHandle.DATA_FLAG_NO_BASEPLATE |
                    EntityArmorStandHandle.DATA_FLAG_IS_SMALL));
        }
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        VirtualEntity displayed = actualEntity();

        // Entity NameTag
        if (config.isNode("nametag") && config.get("nametag.used", true)) {
            ConfigurationNode nametag = config.getNode("nametag");
            boolean visible = nametag.get("visible", true);
            String text = nametag.get("text", "");
            displayed.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME, ChatText.fromMessage(text));
            displayed.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, visible);
        } else {
            displayed.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME, Common.evaluateMCVersion(">=", "1.13")
                    ? null : ChatText.empty());
        }

        // Boat wood type
        if (displayed.getEntityType() == EntityType.BOAT) {
            displayed.getMetaData().set(EntityBoatHandle.DATA_WOOD_TYPE, config.get("boatWoodType", BoatWoodType.OAK));
        }
    }

    @Override
    public void onFocus() {
        actualEntity().setGlowColor(HelperMethods.getFocusGlowColor(this));
    }

    @Override
    public void onBlur() {
        actualEntity().setGlowColor(null);
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return (this.entity != null && this.entity.getEntityId() == entityId) ||
               (this.actual != null && this.actual.getEntityId() == entityId);
    }

    @Override
    public int getMountEntityId() {
        if (this.entity.isMountable()) {
            return this.entity.getEntityId();
        } else {
            return -1;
        }
    }

    @Override
    public void applyPassengerSeatTransform(Matrix4x4 transform) {
        // Minecarts can uniquely pitch - so here we perform the transformation a little differently
        VirtualEntity displayed = actualEntity();
        if (displayed.isMinecart()) {
            transform.translate(0.0, displayed.getMountOffset(), 0.0);
            return;
        }

        // Position on top of the entity is relative - so create a matrix for that purpose
        Matrix4x4 relativeMatrix = new Matrix4x4();
        relativeMatrix.translate(0.0, displayed.getMountOffset(), 0.0);

        // Perform a multiplication with the relative matrix on the left-hand side
        Matrix4x4.multiply(relativeMatrix, transform, transform);
    }

    /**
     * Gets whether movement updates use minecart interpolation, which unlike other entities,
     * update over 5 ticks instead of 3.
     * If seated inside another entity that isn't a Minecart, then this will be false.
     *
     * @return True if this attachment entity is using minecart interpolation
     */
    public boolean isMinecartInterpolation() {
        return this.actual == null && this.entity.isMinecart();
    }

    @Override
    @Deprecated
    public void makeVisible(Player player) {
        makeVisible(getManager().asAttachmentViewer(player));
    }

    @Override
    @Deprecated
    public void makeHidden(Player player) {
        makeHidden(getManager().asAttachmentViewer(player));
    }

    @Override
    public void makeVisible(AttachmentViewer viewer) {
        // Send entity spawn packet
        if (actual != null) {
            actual.spawn(viewer, new Vector());
        }
        entity.spawn(viewer, new Vector());
        if (actual != null) {
            viewer.getVehicleMountController().mount(entity.getEntityId(), actual.getEntityId());
        }
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        // Send entity destroy packet
        if (actual != null) {
            actual.destroy(viewer);
        }
        entity.destroy(viewer);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
        if (this.actual != null) {
            this.actual.updatePosition(transform);
        }
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
        if (this.actual != null) {
            if (this.actual.syncPositionIfMounted()) {
                this.actual.syncPosition(absolute);
            } else {
                this.actual.syncPositionSilent();
            }
        }
    }

    @Override
    public void onTick() {
    }

    /**
     * Whether a particular entity type can be used at all in an entity attachment
     * 
     * @param entityType
     * @return True if supported
     */
    public static boolean isEntityTypeSupported(EntityType entityType) {
        String name = entityType.name();
        if (name.equals("WEATHER") || name.equals("COMPLEX_PART")) {
            return false;
        }

        switch (entityType) {
        case PAINTING:
        case FISHING_HOOK:
        case LIGHTNING:
        case PLAYER:
        case EXPERIENCE_ORB:
        case UNKNOWN:
            return false;
        default:
            break;
        }

        if (VirtualEntity.isLivingEntity(entityType)) {
            return PacketPlayOutSpawnEntityLivingHandle.isEntityTypeSupported(entityType);
        } else {
            return PacketPlayOutSpawnEntityHandle.isEntityTypeSupported(entityType);
        }
    }
}
