package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachment;
import com.bergerkiller.bukkit.tc.controller.MinecartMemberNetwork;

/**
 * Anchor relative to which the attachment is positioned.
 * By default this will be relative to the parent attachment, but it
 * can be set to e.g. front wheel. Other plugins can register a custom
 * anchor point based on other criteria they desire.
 */
public abstract class AttachmentAnchor {
    private static final Map<String, AttachmentAnchor> registry = new LinkedHashMap<String, AttachmentAnchor>();

    /**
     * Default anchor is relative to the parent attachment
     */
    public static AttachmentAnchor DEFAULT = register(new AttachmentAnchor("default") {
        @Override
        public boolean supports(Attachment attachment) {
            return true;
        }

        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
        }
    });

    /**
     * Anchors the front wheel of the Minecart. Only available when used with a Minecart.
     */
    public static AttachmentAnchor FRONT_WHEEL = register(new AttachmentAnchor("front wheel") {
        @Override
        public boolean supports(Attachment attachment) {
            return attachment instanceof CartAttachment;
        }

        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            if (attachment instanceof CartAttachment) {
                MinecartMemberNetwork controller = ((CartAttachment) attachment).getController();
                controller.getMember().getWheels().front().getAbsoluteTransform(transform);
            }
        }
    });

    /**
     * Anchors the front wheel of the Minecart. Only available when used with a Minecart.
     */
    public static AttachmentAnchor BACK_WHEEL = register(new AttachmentAnchor("back wheel") {
        @Override
        public boolean supports(Attachment attachment) {
            return attachment instanceof CartAttachment;
        }

        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            if (attachment instanceof CartAttachment) {
                MinecartMemberNetwork controller = ((CartAttachment) attachment).getController();
                controller.getMember().getWheels().back().getAbsoluteTransform(transform);
            }
        }
    });

    private final String _name;

    public AttachmentAnchor(String name) {
        this._name = name;
    }

    /**
     * Gets the name of the anchor, which is also used to look it up when
     * reading configuration
     * 
     * @return name of the anchor
     */
    public final String getName() {
        return this._name;
    }

    /**
     * Gets whether this anchor supports the attachment specified.
     * Some anchors can not be used with some attachment types of in certain environments,
     * in which case this method should return false. If the anchor is not supported,
     * it will not be shown in the attachment editor as a valid option.
     * 
     * @param attachment
     * @return True if the anchor is supported
     */
    public abstract boolean supports(Attachment attachment);

    /**
     * Applies the anchor to the input parent-relative absolute transformation information.
     * 
     * @param attachment to apply it for
     * @param transform to apply it to
     */
    public abstract void apply(Attachment attachment, Matrix4x4 transform);

    /**
     * Registers a new type of anchor. May overwrite previously registered anchors.
     * 
     * @param anchor to register
     * @return anchor input
     */
    public static <T extends AttachmentAnchor> T register(T anchor) {
        registry.put(anchor.getName(), anchor);
        return anchor;
    }

    /**
     * Unregisters an existing anchor
     * 
     * @param anchor to unregister
     */
    public static void unregister(AttachmentAnchor anchor) {
        registry.remove(anchor.getName());
    }

    /**
     * Gets all registered attachment anchor types
     * 
     * @return values
     */
    public static Collection<AttachmentAnchor> values() {
        return registry.values();
    }

    /**
     * Looks an anchor up by name. If it does not exist in the registry,
     * a fallback anchor is created with this name that defaults to the
     * parent-relative transformation.
     * 
     * @param name
     * @return
     */
    public static AttachmentAnchor find(String name) {
        AttachmentAnchor anchor = registry.get(name);
        if (anchor != null) {
            return anchor;
        } else {
            return new AttachmentAnchor(name) {
                @Override
                public boolean supports(Attachment attachment) {
                    return true;
                }

                @Override
                public void apply(Attachment attachment, Matrix4x4 transform) {
                }
            };
        }
    }

    @Override
    public String toString() {
        return this.getName();
    }
}
