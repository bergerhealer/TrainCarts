package com.bergerkiller.bukkit.tc.controller.global;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Information stored (in memory) about a Player, such as the train or model
 * the player is editing. Players can be retrieved using the
 * {@link TrainCarts#getPlayer(Player)}
 */
public class TrainCartsPlayer implements TrainCarts.Provider {
    private final TrainCarts traincarts;
    private final UUID uuid;
    private WeakReference<Player> player;
    private WeakReference<CartProperties> editedCart;
    private String editedModelName;
    private ConfigurationNode modelClipboard;

    TrainCartsPlayer(TrainCarts traincarts, Player player) {
        this(traincarts, player.getUniqueId());
        this.player = new WeakReference<>(player);
    }

    TrainCartsPlayer(TrainCarts traincarts, UUID uuid) {
        this.traincarts = traincarts;
        this.uuid = uuid;
        this.player = LogicUtil.nullWeakReference();
        this.editedCart = LogicUtil.nullWeakReference();
        this.editedModelName = null;
        this.modelClipboard = null;
    }

    @Override
    public TrainCarts getTrainCarts() {
        return traincarts;
    }

    /**
     * Gets the UUID of the Player this TrainCarts player information is for
     *
     * @return Player UUID
     */
    public UUID getUniqueId() {
        return uuid;
    }

    /**
     * Gets the Player this TrainCarts player information is for, provided that
     * player is currently online
     *
     * @return Online player, <i>null</i> if not currently online
     */
    public Player getOnlinePlayer() {
        Player p = player.get();
        if (p == null || !p.isOnline()) {
            p = Bukkit.getPlayer(uuid);
            if (p != null) {
                player = new WeakReference<>(p);
            }
        }
        return p;
    }

    /**
     * Sends a message if this Player is currently online
     *
     * @param message Message to send
     */
    public void sendMessage(String message) {
        Player p = getOnlinePlayer();
        if (p != null) {
            p.sendMessage(message);
        }
    }

    /**
     * Gets the saved attachment model that this Player is currently editing.
     * This model might not have been created yet, which should be checked with
     * {@link SavedAttachmentModel#isNone()}. If the Player hasn't selected
     * any model configuration to edit, or is editing a Cart instead,
     * returns <i>null</i>.
     *
     * @return Model edited by the Player, or <i>null</i> if not editing anything
     */
    public SavedAttachmentModel getEditedModel() {
        if (editedModelName == null) {
            return null;
        } else {
            return traincarts.getSavedAttachmentModels().getModelOrNone(editedModelName);
        }
    }

    /**
     * Gets the saved attachment model that this Player is currently editing.
     * This model might not have been created yet, in which case a new model
     * configuration is created with a default ITEM attachment inside.
     * If the Player hasn't selected any model configuration to edit, or is editing
     * a Cart instead, returns <i>null</i>.
     *
     * @return Model edited by the Player, or <i>null</i> if not editing anything
     */
    public SavedAttachmentModel getEditedModelInit() {
        if (editedModelName == null) {
            return null;
        } else {
            try {
                return traincarts.getSavedAttachmentModels().setDefaultConfigIfMissing(editedModelName, getOnlinePlayer());
            } catch (IllegalNameException e) {
                editedModelName = null;
                traincarts.getLogger().log(Level.SEVERE, "Unexpected illegal name exception", e);
                return null;
            }
        }
    }

    /**
     * Gets the properties of the cart currently being edited by this Player.
     * Returns null if the player hasn't interacted/selected any cart, or the
     * last one the player selected was removed.
     *
     * @return Cart edited by this Player, or <i>null</i> if none is selected
     */
    public CartProperties getEditedCart() {
        CartProperties edited = editedCart.get();
        if (edited != null && edited.isRemoved()) {
            editedCart = LogicUtil.nullWeakReference();
            edited = null;
        }
        return edited;
    }

    /**
     * Sets the saved attachment model configuration edited by this Player. If
     * previously editing a cart, stops editing that cart and edits this
     * model instead. Automatically refreshes any open attachment
     * editor.
     *
     * @param model Model to edit, <i>null</i> to stop editing anything
     */
    public void editModel(SavedAttachmentModel model) {
        boolean changed = false;
        if (model == null) {
            changed = (editedModelName != null || getEditedCart() != null);
            editedModelName = null;
        } else {
            changed = (!model.getName().equals(editedModelName) || getEditedCart() != null);
            editedModelName = model.getName();
        }
        editedCart = LogicUtil.nullWeakReference();
        if (changed) {
            AttachmentEditor.reloadAttachmentEditorFor(uuid);
        }
    }

    /**
     * Sets the cart edited by the Player. If previously editing a model, will
     * cancel editing that model. Automatically refreshes any open attachment
     * editor.
     *
     * @param member MinecartMember whose Properties to select. Specify <i>null</i> to stop
     *                       editing anything.
     */
    public void editMember(MinecartMember<?> member) {
        editCart(member == null ? null : member.getProperties());
    }

    /**
     * Sets the cart edited by the Player. If previously editing a model, will
     * cancel editing that model. Automatically refreshes any open attachment
     * editor.
     *
     * @param cartProperties Properties to select. Specify <i>null</i> to stop
     *                       editing anything.
     */
    public void editCart(CartProperties cartProperties) {
        if (cartProperties != null && cartProperties.isRemoved()) {
            throw new IllegalArgumentException("Cannot edit a cart that has been removed");
        }

        boolean changed;
        if (cartProperties == null) {
            changed = (editedModelName != null || getEditedCart() != null);
            editedCart = LogicUtil.nullWeakReference();
        } else {
            changed = (getEditedCart() != cartProperties);
            editedCart = new WeakReference<>(cartProperties);
        }
        editedModelName = null;
        if (changed) {
            AttachmentEditor.reloadAttachmentEditorFor(uuid);
        }
    }

    /**
     * Gets the Attachment Configuration that was saved to the Player's clipboard.
     *
     * @return Model configuration on the clipboard, or <i>null</i> if none was saved
     */
    public ConfigurationNode getModelClipboard() {
        return modelClipboard;
    }

    /**
     * Saves model configuration to the Player's clipboard. If null, clears the clipboard.
     *
     * @param attachmentConfig Attachment YAML configuration to save
     */
    public void setModelClipboard(ConfigurationNode attachmentConfig) {
        this.modelClipboard = (attachmentConfig == null) ? null : attachmentConfig.clone();
    }
}
