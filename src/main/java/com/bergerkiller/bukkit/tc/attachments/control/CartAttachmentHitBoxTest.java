package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.helper.HelperMethods;
import org.bukkit.ChatColor;

/**
 * Test version of the hitbox attachment that is used to test the correct
 * functioning of the BKCommonLib OrientedBoundingBox API.
 */
public class CartAttachmentHitBoxTest extends CartAttachmentHitBox {
    public static final AttachmentType TYPE = new BaseHitBoxType() {
        @Override
        public String getID() {
            return "HITBOX_TEST";
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentHitBoxTest();
        }
    };

    private Mode mode = Mode.IDLE;

    @Override
    public void onFocus() {
        setBoxColor(ChatColor.BLACK);
    }

    @Override
    public void onBlur() {
        setBoxColor(mode.getColor());
    }

    @Override
    public void onTick() {
        // Iterate all hitbox test attachments and see if they intersect
        if (!isFocused()) {
            mode = Mode.IDLE;
            for (CartAttachmentHitBoxTest other : getController().getNameLookup().allOfType(CartAttachmentHitBoxTest.class)) {
                if (this == other) {
                    continue;
                }

                if (this.getBoundingBox().isInside(other.getBoundingBox())) {
                    mode = Mode.INSIDE;
                    break;
                } else if (this.getBoundingBox().hasOverlap(other.getBoundingBox())) {
                    mode = Mode.OVERLAP;
                }
            };

            setBoxColor(mode.getColor());
        }
    }

    private enum Mode {
        IDLE(ChatColor.RED),
        OVERLAP(ChatColor.YELLOW),
        INSIDE(ChatColor.GREEN);

        private final ChatColor color;

        Mode(ChatColor color) {
            this.color = color;
        }

        public ChatColor getColor() {
            return color;
        }
    }
}
