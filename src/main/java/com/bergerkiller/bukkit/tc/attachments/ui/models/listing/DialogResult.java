package com.bergerkiller.bukkit.tc.attachments.ui.models.listing;

import org.bukkit.inventory.ItemStack;

/**
 * The result of displaying a dialog to a Player
 */
public final class DialogResult {
    private final ListedItemModel result;
    private final DialogBuilder dialog;
    private final boolean closedWithRootRightClick;

    public DialogResult(DialogBuilder dialog, boolean closedWithRootRightClick) {
        this.result = null;
        this.dialog = dialog;
        this.closedWithRootRightClick = closedWithRootRightClick;
    }

    public DialogResult(DialogBuilder dialog, ListedItemModel result) {
        this.result = result;
        this.dialog = dialog;
        this.closedWithRootRightClick = false;
    }

    /**
     * Gets the state of the dialog options at the time the item model selection
     * dialog was closed. If the user had inputed a new query, the new query is
     * made available here.
     *
     * @return Dialog at the time of closing
     */
    public DialogBuilder dialog() {
        return dialog;
    }

    /**
     * Gets whether an item was selected and the dialog selection was successful.
     * Returns false if the dialog was closed, or if root-right-click cancelling
     * was enabled, closed that way.
     *
     * @return True if an item was selected
     */
    public boolean success() {
        return result != null;
    }

    /**
     * Gets whether the dialog selection was cancelled. Opposite of {@link #success()}
     *
     * @return True if cancelled
     */
    public boolean cancelled() {
        return result == null;
    }

    /**
     * Gets whether item model selection was cancelled by right-clicking the dialog
     * at the root level, rather than closing the dialog.
     *
     * @return True if the dialog was closed by clicking at the root directory level
     *         on the dialog, and {@link DialogBuilder#cancelOnRootRightClick()}
     *         was set.
     */
    public boolean cancelledWithRootRightClick() {
        return closedWithRootRightClick;
    }

    /**
     * Gets the item model that was selected. Returns null if {@link #success()}
     * returns false.
     *
     * @return Selected item model, null if none was selected
     */
    public ListedItemModel selected() {
        return result;
    }

    /**
     * Gets the ItemStack of the item model that was selected. Returns null if
     * {@link #success()} returns false.
     *
     * @return Selected item, null if none was selected
     */
    public ItemStack selectedItem() {
        return result == null ? null : result.item();
    }

    /**
     * Gets the Bare ItemStack of the item model that was selected. This item
     * contains only important predicates, and excludes extra details like descriptions,
     * titles or lores.  Returns null if {@link #success()} returns false.
     *
     * @return Selected bare item, null if none was selected
     */
    public ItemStack selectedBareItem() {
        return result == null ? null : result.bareItem();
    }
}
