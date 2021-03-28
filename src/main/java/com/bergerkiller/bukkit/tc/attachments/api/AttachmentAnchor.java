package com.bergerkiller.bukkit.tc.attachments.api;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.common.math.Quaternion;
import com.bergerkiller.bukkit.common.math.Vector3;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;

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
        public void apply(Attachment attachment, Matrix4x4 transform) {
        }
    });

    /**
     * Disables all rotation information that comes from the parent attachment (or cart).
     * This will cause the attachment to always point in the same direction.
     */
    public static AttachmentAnchor NO_ROTATION = register(new AttachmentAnchor("no rotation") {
        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            Vector3 absolutePosition = transform.toVector3();
            transform.setIdentity();
            transform.translate(absolutePosition);
        }
    });

    /**
     * Aligns the attachment so that it is always pointing upwards (y-coordinate). Only yaw
     * is preserved of the parent attachment.
     */
    public static AttachmentAnchor ALIGN_UP = register(new AttachmentAnchor("align up") {
        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            Vector3 absolutePosition = transform.toVector3();
            Vector forward = transform.getRotation().forwardVector();
            forward.setY(0.0);
            transform.setIdentity();
            transform.translate(absolutePosition);
            if (forward.lengthSquared() > 1e-9) {
                transform.rotate(Quaternion.fromLookDirection(forward));
            }
        }
    });

    /**
     * Moves the seat down so the eyes of the passenger are at its location.
     */
    public static AttachmentAnchor SEAT_EYES = register(new AttachmentAnchor("eyes") {
        @Override
        public boolean supports(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType) {
            return attachmentType == CartAttachmentSeat.TYPE;
        }

        @Override
        public boolean appliedLate() {
            return true;
        }

        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            if (attachment instanceof CartAttachmentSeat) {
                ((CartAttachmentSeat) attachment).transformToEyes(transform);
            }
        }
    });

    /**
     * Marker attachment anchor for seat attachments to indicate the position of the seat should be
     * set to the parent attachment's default passenger position. This will attach this attachment
     * to the parent attachment, if possible, or use a 0/0/0 transform otherwise.
     */
    public static AttachmentAnchor SEAT_PARENT = register(new AttachmentAnchor("seat parent") {
        @Override
        public boolean supports(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType) {
            return attachmentType == CartAttachmentSeat.TYPE;
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
        public boolean supports(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType) {
            return managerType.isAssignableFrom(AttachmentControllerMember.class);
        }

        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            if (attachment.getManager() instanceof AttachmentControllerMember) {
                AttachmentControllerMember controller = (AttachmentControllerMember) attachment.getManager();
                controller.getMember().getWheels().front().getAbsoluteTransform(transform);
            }
        }
    });

    /**
     * Anchors the front wheel of the Minecart. Only available when used with a Minecart.
     */
    public static AttachmentAnchor BACK_WHEEL = register(new AttachmentAnchor("back wheel") {
        @Override
        public boolean supports(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType) {
            return managerType.isAssignableFrom(AttachmentControllerMember.class);
        }

        @Override
        public void apply(Attachment attachment, Matrix4x4 transform) {
            if (attachment.getManager() instanceof AttachmentControllerMember) {
                AttachmentControllerMember controller = (AttachmentControllerMember) attachment.getManager();
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
     * Gets whether this anchor supports the attachment type specified.
     * Some anchors can not be used with some attachment types,
     * in which case this method should return false. If the anchor is not supported,
     * it will not be shown in the attachment editor as a valid option.
     * 
     * @param managerType The type of attachment manager that will host the attachment
     * @param attachmentType The type of attachment
     * @return True if the anchor is supported
     */
    public boolean supports(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType) {
        return true;
    }

    /**
     * Whether the attachment anchor is applied 'late', meaning the anchor transformation is performed
     * after the translation and rotation of the attachment itself.
     * 
     * @return True if applied late
     */
    public boolean appliedLate() {
        return false;
    }

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
     * parent-relative transformation.<br>
     * <br>
     * The attachment type and manager type parameter is used to check whether the anchor supports the one
     * specified by name. If it does not, a fallback no-operation anchor is returned instead.
     * 
     * @param managerType The Class type of the AttachmentManager used to host the attachment and anchor
     * @param attachmentType The type of attachment for which this anchor is used
     * @param Name name of the anchor type
     * @return Attachment Anchor, returns a default No-Operation one if none match
     */
    public static AttachmentAnchor find(Class<? extends AttachmentManager> managerType, AttachmentType attachmentType, String name) {
        AttachmentAnchor anchor = registry.get(name);
        if (anchor != null && anchor.supports(managerType, attachmentType)) {
            return anchor;
        } else {
            return new AttachmentAnchor(name) {
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
