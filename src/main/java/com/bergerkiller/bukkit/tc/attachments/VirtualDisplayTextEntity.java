package com.bergerkiller.bukkit.tc.attachments;

import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.common.wrappers.DataWatcher;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.generated.net.minecraft.world.entity.DisplayHandle;
import org.bukkit.Color;

public class VirtualDisplayTextEntity extends VirtualDisplayEntity {
    // Properties
    private ChatText text;
    private int styleFlags = 0;
    private int backgroundColor = 1073741824;
    private double opacity = 1.0;
    private byte opacityByte = -1;

    /**
     * Creates DataWatchers with the base metadata default values for a new Block display entity
     */
    public static final DataWatcher.Prototype TEXT_DISPLAY_METADATA = BASE_DISPLAY_METADATA.modify()
            .setClientDefault(DisplayHandle.TextDisplayHandle.DATA_TEXT, ChatText.empty())
            .setClientDefault(DisplayHandle.TextDisplayHandle.DATA_LINE_WIDTH, 200)
            .setClientDefault(DisplayHandle.TextDisplayHandle.DATA_BACKGROUND_COLOR, 1073741824)
            .setClientByteDefault(DisplayHandle.TextDisplayHandle.DATA_TEXT_OPACITY, -1)
            .setClientByteDefault(DisplayHandle.TextDisplayHandle.DATA_STYLE_FLAGS, 0)
            .setClientByteDefault(DisplayHandle.DATA_BILLBOARD_RENDER_CONSTRAINTS, DisplayHandle.BILLBOARD_RENDER_FIXED)
            .create();

    public VirtualDisplayTextEntity(AttachmentManager manager) {
        super(manager, TEXT_DISPLAY_ENTITY_TYPE, TEXT_DISPLAY_METADATA.create());
        text = null;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setOpacity(double newOpacity) {
        byte newOpacityByte = (byte) (newOpacity * 255.0);
        if (opacityByte != newOpacityByte) {
            opacityByte = newOpacityByte;
            metadata.set(DisplayHandle.TextDisplayHandle.DATA_TEXT_OPACITY, newOpacityByte);
        }
    }

    public int getStyleFlags() {
        return styleFlags;
    }

    /**
     * Sets new flags. Flags are found
     * in {@link DisplayHandle.TextDisplayHandle} as STYLE_FLAG_ constants.
     *
     * @param newFlags New flags to set
     */
    public void setStyleFlags(int newFlags) {
        if (styleFlags != newFlags) {
            this.styleFlags = newFlags;
            this.metadata.setByte(DisplayHandle.TextDisplayHandle.DATA_STYLE_FLAGS, newFlags);
        }
    }

    /**
     * Sets or clears one or more flags, leaving other flags unchanged. Flags are found
     * in {@link DisplayHandle.TextDisplayHandle} as STYLE_FLAG_ constants.
     *
     * @param flagChanges Flags to set or clear
     * @param set True to set, false to clear the flag
     */
    public void updateStyleFlags(int flagChanges, boolean set) {
        setStyleFlags(set ? (this.styleFlags | flagChanges) : (this.styleFlags & ~flagChanges));
    }

    public void setBackgroundColor(Color color) {
        setBackgroundColor(color.asARGB());
    }

    public void setBackgroundColor(int colorRGB) {
        if (this.backgroundColor != colorRGB) {
            this.backgroundColor = colorRGB;
            this.metadata.set(DisplayHandle.TextDisplayHandle.DATA_BACKGROUND_COLOR, colorRGB);
        }
    }

    public ChatText getText() {
        return text;
    }

    public void setText(ChatText text) {
        if (!LogicUtil.bothNullOrEqual(this.text, text)) {
            this.text = text;
            this.metadata.set(DisplayHandle.TextDisplayHandle.DATA_TEXT, text);
            syncMeta(); // Changes in text should occur immediately
        }
    }
}
