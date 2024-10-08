package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import java.util.Collections;
import java.util.Random;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.utils.MathUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.particle.PhysicalMemberPreview;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

/**
 * Menu for setting up the cart length, wheel distance, wheel centering,
 * banking and movement drag parameters.
 */
public class PhysicalMenu extends MapWidgetMenu {
    private static final int PREVIEW_OFFSET = 5;
    private static final int PREVIEW_HEIGHT = 10;
    private static final int NUMBERBOX_OFFSET = 27;
    private static final int NUMBERBOX_STEP = 21;
    private static final int NUMBERBOX_HEIGHT = 11;
    private final MapTexture wheelTexture;
    private PhysicalMemberPreview preview;
    private int ticksPreviewVisible = 0;

    public PhysicalMenu() {
        this.setBounds(5, 15, 118, 107);
        this.setBackgroundColor(MapColorPalette.COLOR_ORANGE);
        this.wheelTexture = MapTexture.loadPluginResource(TrainCarts.plugin,
                "com/bergerkiller/bukkit/tc/textures/attachments/wheel.png");
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // If there is no edited cart, then we can't show a "physical" preview. Abort.
        if (this.getAttachment().getEditor().getEditedCart() != null) {
            // Shows a preview in the real world
            preview = new PhysicalMemberPreview(this.getAttachment().getEditor().getEditedCart(), () -> {
                if (ticksPreviewVisible > 0 && display != null) {
                    return display.getOwners();
                } else {
                    return Collections.emptySet();
                }
            });
        }

        // Color of the labels for the number boxes
        byte lblColor = MapColorPalette.getColor(152, 89, 36);

        this.addWidget(new MapWidgetNumberBox() { // Cart Length
            @Override
            public void onAttached() {
                super.onAttached();
                this.setRange(0.0, Double.POSITIVE_INFINITY);
                this.setValue(getConfig().get("cartLength", 1.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Cart Length";
            }

            @Override
            public void onResetValue() {
                setValue(AttachmentModel.DEFAULT_CART_LENGTH);
            }

            @Override
            public void onValueChanged() {
                getConfig().set("cartLength", getValue());
                onChanged();
            }
        }).setBounds(10, NUMBERBOX_OFFSET + 0 * NUMBERBOX_STEP, 100, NUMBERBOX_HEIGHT);
        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Cart Length").setPosition(20, NUMBERBOX_OFFSET+0*NUMBERBOX_STEP - 8);

        this.addWidget(new MapWidgetNumberBox() { // Cart Coupler Length
            @Override
            public void onAttached() {
                super.onAttached();

                // Note: You can go as far negative as you like
                //       If higher than cart length, the carts will just occupy the same spot
                this.setRange(-100.0, Math.max(0.1, TCConfig.cartDistanceGapMax));
                this.setInitialValue(getConfig().getOrDefault("cartCouplerLength", 0.5 * TCConfig.cartDistanceGap));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Coupler Length";
            }

            @Override
            public void onResetValue() {
                setValue(0.5 * TCConfig.cartDistanceGap);
            }

            @Override
            public void onValueChanged() {
                if (getValue() == (0.5 * TCConfig.cartDistanceGap)) {
                    getConfig().remove("cartCouplerLength");
                } else {
                    getConfig().set("cartCouplerLength", getValue());
                }
                onChanged();
            }
        }).setBounds(10, NUMBERBOX_OFFSET + 1 * NUMBERBOX_STEP, 100, NUMBERBOX_HEIGHT);
        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Coupler Length").setPosition(20, NUMBERBOX_OFFSET+1*NUMBERBOX_STEP - 8);

        this.addWidget(new MapWidgetNumberBox() { // Wheel Distance
            @Override
            public void onAttached() {
                super.onAttached();
                this.setRange(0.0, Double.POSITIVE_INFINITY);
                this.setValue(getConfig().get("wheelDistance", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Wheel Distance";
            }

            @Override
            public void onValueChanged() {
                getConfig().set("wheelDistance", getValue());
                onChanged();
            }
        }).setBounds(10, NUMBERBOX_OFFSET + 2 * NUMBERBOX_STEP, 100, NUMBERBOX_HEIGHT);
        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Wheel Distance").setPosition(20, NUMBERBOX_OFFSET+2*NUMBERBOX_STEP - 8);

        this.addWidget(new MapWidgetNumberBox() { // Wheel Center
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getConfig().get("wheelCenter", 0.0));
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Wheel Center Offset";
            }

            @Override
            public void onValueChanged() {
                getConfig().set("wheelCenter", getValue());
                onChanged();
            }
        }).setBounds(10, NUMBERBOX_OFFSET + 3 * NUMBERBOX_STEP, 100, NUMBERBOX_HEIGHT);
        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Wheel Offset").setPosition(20, NUMBERBOX_OFFSET+3*NUMBERBOX_STEP - 8);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (this.preview != null) {
            this.preview.hide();
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.preview != null) {
            this.preview.update();
        }
        if (--this.ticksPreviewVisible < 0) {
            this.ticksPreviewVisible = 0;
        }
    }

    private void onChanged() {
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        this.ticksPreviewVisible = 100;
    }

    public ConfigurationNode getConfig() {
        return this.attachment.getConfig().getNode("physical");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

    @Override
    public void onStatusChanged(MapStatusEvent event) {
        if (event.isName("changed")) {
            this.invalidate();
        }
    }

    @Override
    public void onDraw() {
        super.onDraw();

        // Get physical properties and scale them to be within UI dimensions
        double cartLength = getConfig().get("cartLength", 1.0);
        double wheelDistance = getConfig().get("wheelDistance", 0.0);
        double wheelCenter = getConfig().get("wheelCenter", 0.0);
        double cartLengthFactor = 20.0; // Amount of pixels per unit length
        double maxCartLength = 5.0; // Maximum cart length filling the screen
        boolean isMaxScale = (cartLength > maxCartLength);
        double scaleFactor = cartLengthFactor * (isMaxScale ? (maxCartLength / cartLength) : 1.0);
        cartLength *= scaleFactor;
        wheelDistance *= scaleFactor;
        wheelCenter *= scaleFactor;
        if (isMaxScale) {
            cartLength = cartLengthFactor * maxCartLength;
        }

        // Draw the Minecart hull based on the cart length
        int hull_x = MathUtil.floor((0.5 * this.getWidth()) - (0.5 * cartLength));
        int hull_y = PREVIEW_OFFSET;
        int hull_w = MathUtil.ceil(cartLength);
        int hull_h = PREVIEW_HEIGHT;
        {
            view.drawRectangle(hull_x, hull_y, hull_w, hull_h, MapColorPalette.COLOR_BLACK);
            Random rand = new Random(12345678L);
            for (int px = 1; px < (hull_w - 1); px++) {
                for (int py = 1; py < (hull_h - 1); py++) {
                    byte color = (byte) (128 + rand.nextInt(40));
                    view.drawPixel(hull_x+px, hull_y+py, MapColorPalette.getColor(color, color, color));
                }
            }
        }

        // Draw two wheels
        int wheel_x1 = MathUtil.floor((0.5 * this.getWidth()) - (0.5 * wheelDistance) + wheelCenter);
        int wheel_x2 = MathUtil.ceil((0.5 * this.getWidth()) + (0.5 * wheelDistance) + wheelCenter);
        int wheel_y = hull_y + hull_h - 1;
        {
            drawWheel(MathUtil.clamp(wheel_x1, hull_x, hull_x + hull_w - 1), wheel_y);
            drawWheel(MathUtil.clamp(wheel_x2, hull_x, hull_x + hull_w - 1), wheel_y);
        }
    }

    private final void drawWheel(int x, int y) {
        this.view.draw(this.wheelTexture,
                x - MathUtil.floor(0.5 * this.wheelTexture.getWidth()),
                y - MathUtil.floor(0.5 * this.wheelTexture.getHeight()));
    }
}
