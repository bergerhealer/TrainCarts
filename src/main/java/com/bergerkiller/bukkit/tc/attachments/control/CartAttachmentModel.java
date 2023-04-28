package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;

public class CartAttachmentModel extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return MODEL_TYPE_ID;
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/model.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentModel();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            MapWidgetSubmitText textBox = new MapWidgetSubmitText() {
                @Override
                public void onAttached() {
                    this.setDescription("Enter Model Name");
                }

                @Override
                public void onAccept(String text) {
                    attachment.getConfig().set(AttachmentConfig.Model.MODEL_NAME_CONFIG_KEY, text);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);
                    attachment.resetIcon();
                }
            };

            tab.addWidget(textBox);
            tab.addWidget(new MapWidgetText().setText("Current Model:")).setBounds(0, 10, 100, 16);
            tab.addWidget(new MapWidgetText() {
                        @Override
                        public void onTick() {
                            setText("\"" + attachment.getConfig().get(AttachmentConfig.Model.MODEL_NAME_CONFIG_KEY, "") + "\"");
                        }
                    }).setAlignment(MapFont.Alignment.MIDDLE)
                    .setBounds(0, 30, 100, 16);

            tab.addWidget(new MapWidgetButton() {
                @Override
                public void onAttached() {
                    this.setText("Edit Model Name");
                }

                @Override
                public void onActivate() {
                    textBox.activate();
                }
            }).setBounds(0, 60, 100, 16);
        }
    };

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
    }
}
