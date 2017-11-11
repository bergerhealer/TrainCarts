package com.bergerkiller.bukkit.tc.attachments.config;

import java.util.ArrayList;
import java.util.List;

public class CartAttachmentNode {
    private List<CartAttachmentNode> _attachments = new ArrayList<CartAttachmentNode>(1);
    private CartAttachmentType _type = CartAttachmentType.EMPTY;

    public CartAttachmentType getType() {
        return this._type;
    }

    public void setType(CartAttachmentType type) {
        this._type = type;
    }

    public List<CartAttachmentNode> getAttachments() {
        return this._attachments;
    }

    public void addAttachment(CartAttachmentNode attachment) {
        this._attachments.add(attachment);
    }

    public boolean removeAttachment(CartAttachmentNode attachment) {
        if (!this._attachments.remove(attachment)) {
            return false;
        }

        return true;
    }
}
