package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.bases.IntVector3;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.light.LightAPIController;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

/**
 * Uses LightAPI (or LightAPI-Fork) to create moving light sources in the world.
 * Only available when the LightAPI plugin is available.
 */
public class CartAttachmentLight extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        private int numRegistries = 0;

        @Override
        public String getID() {
            return "LIGHT";
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/light.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentLight();
        }

        @Override
        public void getDefaultConfig(ConfigurationNode config) {
            config.set("lightType", "BLOCK");
            config.set("lightLevel", 15);
        }

        @Override
        public void onRegister(AttachmentTypeRegistry registry) {
            numRegistries++;
        }

        @Override
        public void onUnregister(AttachmentTypeRegistry registry) {
            if (--numRegistries <= 0) {
                LightAPIController.disable();
            }
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            tab.addWidget(new MapWidgetButton() { // Lock rotation toggle button
                private boolean skylight = false;

                @Override
                public void onAttached() {
                    super.onAttached();
                    this.skylight = attachment.getConfig().get("lightType", "BLOCK").equalsIgnoreCase("SKY");
                    updateText();
                }

                private void updateText() {
                    this.setText("Type: " + (skylight ? "SKY":"BLOCK"));
                }

                @Override
                public void onActivate() {
                    this.skylight = !this.skylight;
                    updateText();
                    attachment.getConfig().set("lightType", this.skylight ? "SKY" : "BLOCK");
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    attachment.resetIcon();
                    display.playSound(SoundEffect.CLICK);
                }
            }).setBounds(7, 10, 100-14, 16);

            tab.addWidget(new MapWidgetNumberBox() { // Light level number box

                @Override
                public void onAttached() {
                    super.onAttached();
                    this.setRange(1.0, 15.0);
                    this.setIncrement(1.0);
                    this.setValue(attachment.getConfig().get("lightLevel", 15));
                }

                @Override
                public String getValueText() {
                    return "Level: " + Integer.toString((int) this.getValue());
                }
 
                @Override
                public void onValueChanged() {
                    attachment.getConfig().set("lightLevel", (int) this.getValue());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                }
            }).setBounds(0, 30, 100, 16);

            tab.addWidget(new MapWidgetText() { // LightAPI disclaimer
                @Override
                public void onAttached() {
                    super.onAttached();
                    this.setText("Powered by LightAPI");
                    this.setColor(MapColorPalette.getColor(0, 1, 79));
                    this.setShadowColor(MapColorPalette.getColor(0, 0, 220));
                }
            }).setBounds(0, 74, 100, 10);
        }
    };

    private IntVector3 prev_block = null;
    private LightAPIController controller = null;
    private int lightLevel = 15;
    private boolean lightVisible = true;

    @Override
    public void onAttached() {
        boolean isSky = getConfig().get("lightType", "BLOCK").equalsIgnoreCase("SKY");
        controller = LightAPIController.get(this.getManager().getWorld(), isSky);
        lightLevel = getConfig().get("lightLevel", 15);
        lightVisible = !HelperMethods.hasInactiveParent(this);
    }

    @Override
    public void onDetached() {
        if (prev_block != null) {
            controller.remove(prev_block, lightLevel);
            prev_block = null;
        }
    }

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }

    @Override
    public void onActiveChanged(boolean active) {
        lightVisible = active;
        if (!active && prev_block != null) {
            controller.remove(prev_block, lightLevel);
            prev_block = null;
        }
    }

    @Override
    public void onTick() {
        Vector pos_d = this.getTransform().toVector();
        IntVector3 pos = new IntVector3(pos_d.getX(), pos_d.getY(), pos_d.getZ());

        if (lightVisible) {
            if (prev_block == null) {
                controller.add(pos, lightLevel);
                prev_block = pos;
            } else if (!pos.equals(prev_block)) {
                controller.move(prev_block, pos, lightLevel);
                prev_block = pos;
            }
        } else if (prev_block != null) {
            controller.remove(prev_block, lightLevel);
            prev_block = null;
        }
    }

    @Override
    public void onMove(boolean absolute) {
    }
}
