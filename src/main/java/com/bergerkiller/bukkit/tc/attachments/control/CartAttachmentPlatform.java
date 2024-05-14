package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.generated.net.minecraft.world.entity.monster.EntityShulkerHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentViewer;
import com.bergerkiller.generated.net.minecraft.world.entity.EntityHandle;

public class CartAttachmentPlatform extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "PLATFORM";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/platform.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentPlatform();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            // Shulker box color selector
            tab.addWidget(new MapWidgetText())
                    .setText("Shulker Color")
                    .setFont(MapFont.MINECRAFT)
                    .setColor(MapColorPalette.COLOR_RED)
                    .setBounds(15, 6, 50, 11);
            final MapWidget boatTypeSelector = tab.addWidget(new MapWidgetSelectionBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.addItem(Color.DEFAULT.name());
                    for (Color color : Color.values()) {
                        if (color != Color.DEFAULT) {
                            this.addItem(color.name());
                        }
                    }
                    this.setSelectedItem(attachment.getConfig().getOrDefault("shulkerColor", Color.DEFAULT).name());
                }

                @Override
                public void onSelectedItemChanged() {
                    attachment.getConfig().set("shulkerColor", this.getSelectedItem());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setBounds(0, 15, 100, 12);
        }
    };

    private VirtualEntity actual;
    private VirtualEntity entity;

    @Override
    public void onDetached() {
        super.onDetached();
        this.entity = null;
        this.actual = null;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        this.actual = new VirtualEntity(this.getManager());
        this.actual.setEntityType(EntityType.SHULKER);
        this.actual.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.actual.getMetaData().setClientByteDefault(EntityShulkerHandle.DATA_COLOR, Color.DEFAULT.ordinal());

        // Shulker boxes fail to move, and must be inside a vehicle to move at all
        // Handle this logic here. It seems that the position of the chicken is largely irrelevant.
        this.entity = new VirtualEntity(this.getManager());
        this.entity.setEntityType(EntityType.CHICKEN);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.setRelativeOffset(0.0, -0.32, 0.0);
    }

    @Override
    public void onLoad(ConfigurationNode config) {
        Color color = config.getOrDefault("shulkerColor", Color.DEFAULT);
        this.actual.getMetaData().set(EntityShulkerHandle.DATA_COLOR, (byte) color.ordinal());
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity.getEntityId() == entityId ||
               this.actual.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        return this.actual.getEntityId();
    }

    @Override
    public void applyPassengerSeatTransform(Matrix4x4 transform) {
        Matrix4x4 relativeMatrix = new Matrix4x4();
        relativeMatrix.translate(0.0, 1.0, 0.0);
        Matrix4x4.multiply(relativeMatrix, transform, transform);
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
        actual.spawn(viewer, new Vector());
        entity.spawn(viewer, new Vector());
        viewer.getVehicleMountController().mount(entity.getEntityId(), actual.getEntityId());
    }

    @Override
    public void makeHidden(AttachmentViewer viewer) {
        // Send entity destroy packet
        actual.destroy(viewer);
        entity.destroy(viewer);
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
        this.actual.updatePosition(transform);
        this.actual.syncMetadata();
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);

        // Must not send move packets of the mounted shulker. This causes glitches since MC 1.17
        this.actual.syncPositionSilent();
    }

    @Override
    public void onTick() {
    }

    /**
     * Shulker color, taken from nms EnumColor
     */
    public enum Color {
        WHITE,
        ORANGE,
        MAGENTA,
        LIGHT_BLUE,
        YELLOW,
        LIME,
        PINK,
        GRAY,
        LIGHT_GRAY,
        CYAN,
        PURPLE,
        BLUE,
        BROWN,
        GREEN,
        RED,
        BLACK,
        DEFAULT
    }
}
