package com.bergerkiller.bukkit.tc.attachments.ui;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;

/**
 * Caches an attachment type icon so expensive icon generation is done only once.
 */
public class CachedAttachmentIcon {
    private static final MapTexture MISSING_ICON = MapTexture.loadResource(CachedAttachmentIcon.class,
            "/com/bergerkiller/bukkit/tc/textures/attachments/missing.png");

    private final TypeConfigAccessor accessor;
    private MapTexture icon;

    public CachedAttachmentIcon(TypeConfigAccessor accessor) {
        this.accessor = accessor;
    }

    public CachedAttachmentIcon(AttachmentType type, ConfigurationNode config) {
        this(new TypeConfigAccessor() {
            @Override
            public AttachmentType getType() {
                return type;
            }

            @Override
            public ConfigurationNode getConfig() {
                return config;
            }
        });
    }

    public MapTexture getIcon() {
        if (this.icon == null) {
            AttachmentType type = this.accessor.getType();
            this.icon = (type == null) ? MISSING_ICON : type.getIcon(this.accessor.getConfig());
        }
        return this.icon;
    }

    public void invalidate() {
        this.icon = null;
    }

    public interface TypeConfigAccessor {
        AttachmentType getType();
        ConfigurationNode getConfig();
    }
}
