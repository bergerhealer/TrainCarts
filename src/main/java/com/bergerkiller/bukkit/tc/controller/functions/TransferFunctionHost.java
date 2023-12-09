package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSequencer;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.controller.functions.inputs.TransferFunctionInput;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

/**
 * Owns transfer functions. Provides information about what inputs
 * are available and other information that changes transfer function
 * behavior.
 */
public interface TransferFunctionHost extends TrainCarts.Provider {

    /**
     * Gets the registry used for loading/saving transfer functions
     *
     * @return Transfer Function Registry
     */
    TransferFunctionRegistry getRegistry();

    /**
     * Registers a (new) input to be kept updated regularly. The input referenced source was
     * already used before, the existing source is returned instead of the input source.
     * Caller should right afterwards register itself as a recipient for the returned
     * referenced source. Not multithread-safe.
     *
     * @param source ReferencedSource to keep updated
     * @return ReferencedSource result that should be used to read the input
     */
    TransferFunctionInput.ReferencedSource registerInputSource(TransferFunctionInput.ReferencedSource source);

    /**
     * Gets the MinecartMember cart properties of the cart thats owns this transfer function.
     * If no member is known, returns null.
     *
     * @return Properties, null if not available or known
     */
    default CartProperties getCartProperties() {
        MinecartMember<?> member = getMember();
        return member == null ? null : member.getProperties();
    }

    /**
     * Gets the MinecartMember that owns this transfer function. Inputs are about this member.
     *
     * @return Member, null if not available or known
     */
    MinecartMember<?> getMember();

    /**
     * Gets the attachment that owns this transfer function.
     *
     * @return Attachment, null if not available or known
     */
    Attachment getAttachment();

    /**
     * Gets whether this host is part of the {@link CartAttachmentSequencer}
     *
     * @return True if a sequencer
     */
    boolean isSequencer();

    /**
     * Loads a Transfer Function from configuration
     *
     * @param config Configuration to load
     * @return Loaded TransferFunction
     */
    default TransferFunction loadFunction(ConfigurationNode config) {
        return getRegistry().load(this, config);
    }

    /**
     * Saves a Transfer Function to configuration
     *
     * @param function Transfer Function to save
     * @return Saved Configuration
     */
    default ConfigurationNode saveFunction(TransferFunction function) {
        return getRegistry().save(this, function);
    }
}
