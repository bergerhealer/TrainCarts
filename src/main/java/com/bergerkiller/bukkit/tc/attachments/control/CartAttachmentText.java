package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.VirtualEntity;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.generated.net.minecraft.server.EntityHandle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class CartAttachmentText extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return "TEXT";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/text.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentText();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            MapWidgetSubmitText textBox = new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    this.setDescription("Enter text");
                }

                @Override
                public void onAccept(String text) {
                    attachment.getConfig().set("text", text);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                }
            };

            tab.addWidget(textBox);
            tab.addWidget(new MapWidgetText().setText("Current Text:")).setBounds(0, 10, 100, 16);
            tab.addWidget(new MapWidgetText() {
                @Override
                public void onTick() {
                    setText("\"" + attachment.getConfig().get("text", "") + "\"");
                }
            }).setAlignment(MapFont.Alignment.MIDDLE)
              .setBounds(0, 30, 100, 16);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onAttached() {
                    this.setText("Edit Text");
                }

                @Override
                public void onActivate() {
                    textBox.activate();
                }
            }).setBounds(0, 60, 100, 16);
        }
    };

    private VirtualEntity entity;

    @Override
    public void onAttached() {
        super.onAttached();
        String text = this.getConfig().get("text", " ");
        if (text.length() == 0) {
            text = " ";
        }

        this.entity = new VirtualEntity(this.getManager());

        this.entity.setEntityType(EntityType.ARMOR_STAND);
        // this.entity.setSyncMode(VirtualEntity.SyncMode.SEAT);
        this.entity.getMetaData().set(EntityHandle.DATA_FLAGS, (byte) EntityHandle.DATA_FLAG_INVISIBLE);
        this.entity.getMetaData().set(EntityHandle.DATA_NO_GRAVITY, true);
        this.entity.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME_VISIBLE, true);
        this.entity.getMetaData().set(EntityHandle.DATA_CUSTOM_NAME, ChatText.fromMessage(text));
        this.entity.setRelativeOffset(0, -1.6, 0);
    }

    @Override
    public void onTick() {
    }

    @Override
    public boolean containsEntityId(int entityId) {
        return this.entity != null && this.entity.getEntityId() == entityId;
    }

    @Override
    public int getMountEntityId() {
        return this.entity.getEntityId();
    }

    @Override
    public void onTransformChanged(Matrix4x4 transform) {
        this.entity.updatePosition(transform);
    }

    @Override
    public void onMove(boolean absolute) {
        this.entity.syncPosition(absolute);
    }

    @Override
    public void makeVisible(Player viewer) {
        entity.spawn(viewer, new Vector(0.0, 0.0, 0.0));
    }

    @Override
    public void makeHidden(Player viewer) {
        entity.destroy(viewer);
    }

}
