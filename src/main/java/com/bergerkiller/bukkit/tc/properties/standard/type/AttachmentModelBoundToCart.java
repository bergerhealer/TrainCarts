package com.bergerkiller.bukkit.tc.properties.standard.type;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

/**
 * Binds an attachment model to a cart, so that once changes are
 * made to its configuration, the configuration is set in the cart
 * properties config.
 */
public class AttachmentModelBoundToCart extends AttachmentModel {
    private final CartProperties _owner;
    private boolean _isBoundToOwner;

    public AttachmentModelBoundToCart(CartProperties owner, ConfigurationNode cartConfig) {
        super(cartConfig);
        this._owner = owner;
        this._isBoundToOwner = true;
    }

    public void setBoundToOwner(boolean bound) {
        this._isBoundToOwner = bound;
    }

    private void bindToOwner() {
        if (!this._isBoundToOwner) {
            this._isBoundToOwner = true;
            this._owner.getConfig().set("model", this.getConfig());
        }
    }

    @Override
    protected void onConfigChanged(boolean notify) {
        bindToOwner();
        super.onConfigChanged(notify);
    }

    @Override
    protected void onConfigNodeChanged(int[] targetPath, ConfigurationNode config, boolean notify) {
        bindToOwner();
        super.onConfigNodeChanged(targetPath, config, notify);
    }
}
