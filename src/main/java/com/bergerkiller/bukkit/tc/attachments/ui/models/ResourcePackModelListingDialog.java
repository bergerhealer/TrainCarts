package com.bergerkiller.bukkit.tc.attachments.ui.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.DragType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

import net.md_5.bungee.api.ChatColor;

/**
 * Displays the contents of {@link ResourcePackModelListing} in an inventory menu dialog
 * for a single player.
 */
class ResourcePackModelListingDialog implements Listener {
    private static final int DISPLAYED_ITEM_COUNT = 4 * 9; // Top 4 rows
    private static Map<Player, ResourcePackModelListingDialog> shownTo = new HashMap<>();
    private final ResourcePackModelListing.DialogBuilder options;
    private final CompletableFuture<Optional<ResourcePackModelListing.ListedItemModel>> future;
    private final UIButton btnPrevPage = new PrevPageButton();
    private final UIButton btnNextPage = new NextPageButton();
    private final UIButton btnSearch = new SearchButton();
    private final List<UIButton> buttons = Arrays.asList(btnPrevPage, btnNextPage, btnSearch);
    private Inventory inventory;
    private ResourcePackModelListing.ListedEntry current;
    private List<? extends ResourcePackModelListing.ListedEntry> currentItems;
    private int page = 0;

    public static CompletableFuture<Optional<ResourcePackModelListing.ListedItemModel>> show(
            ResourcePackModelListing.DialogBuilder dialogOptions
    ) {
        ResourcePackModelListingDialog dialog = new ResourcePackModelListingDialog(dialogOptions.clone());

        ResourcePackModelListingDialog prev = shownTo.put(dialog.player(), dialog);
        if (prev != null) {
            prev.close();
        }

        dialog.open();
        return dialog.future;
    }

    public static void closeAll() {
        List<ResourcePackModelListingDialog> dialogs = new ArrayList<>(shownTo.values());
        shownTo.clear();
        dialogs.forEach(ResourcePackModelListingDialog::close);
    }

    public static void closeAllByPlugin(Plugin plugin) {
        for (ResourcePackModelListingDialog dialog : new ArrayList<>(shownTo.values())) {
            if (dialog.options.plugin() == plugin) {
                shownTo.remove(dialog.options.player());
                dialog.close();
            }
        }
    }

    public static void close(Player player) {
        ResourcePackModelListingDialog prev = shownTo.remove(player);
        if (prev != null) {
            prev.close();
        }
    }

    private ResourcePackModelListingDialog(ResourcePackModelListing.DialogBuilder options) {
        this(options, new CompletableFuture<>());
        if (!options.selectHandlers.isEmpty() || !options.cancelHandlers.isEmpty()) {
            this.future.thenAccept(opt_action -> {
                if (opt_action.isPresent()) {
                    ResourcePackModelListing.ListedItemModel result = opt_action.get();
                    for (Consumer<ResourcePackModelListing.ListedItemModel> acceptor : options.selectHandlers) {
                        acceptor.accept(result);
                    }
                } else {
                    options.cancelHandlers.forEach(Runnable::run);
                }
            });
        }
    }

    private ResourcePackModelListingDialog(
            ResourcePackModelListing.DialogBuilder options,
            CompletableFuture<Optional<ResourcePackModelListing.ListedItemModel>> future
    ) {
        this.options = options;
        this.future = future;
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, options.plugin());

        inventory = Bukkit.createInventory(player(), 54, options.title);
        setListedEntry(options.listing().root().compact());
        player().openInventory(inventory);
    }

    public void close() {
        cancelDialog();
        if (player().getOpenInventory() != null && player().getOpenInventory().getTopInventory() == this.inventory) {
            player().closeInventory();
        }
    }

    private Player player() {
        return options.player();
    }

    private ClickAction onItemClicked(ResourcePackModelListing.ListedItemModel item) {
        if (options.creativeMenu) {
            return ClickAction.CREATIVE_CLICK_PICKUP;
        }

        // Accept this item and close the dialog
        future.complete(Optional.of(item));
        close();
        return ClickAction.HANDLED;
    }

    private ClickAction handleClick(int clickedSlot, boolean isRightClick, ItemStack cursorItem) {
        // If this is a creative menu and a cursor item is held, allow dropping (deleting) the item
        // like a traditional creative menu would do.
        // If the cursor item matches with the item held in this slot in this inventory (except count),
        // allow increasing the count by one until the stack size is reached.
        if (options.creativeMenu && !ItemUtil.isEmpty(cursorItem) && clickedSlot >= 0 && clickedSlot < (6*9)) {
            ItemStack itemInSlot = this.inventory.getItem(clickedSlot);
            if (!isRightClick && itemInSlot != null && ItemUtil.equalsIgnoreAmount(itemInSlot, cursorItem)) {
                if (cursorItem.getAmount() < cursorItem.getMaxStackSize()) {
                    return ClickAction.CREATIVE_CLICK_INCREASE_COUNT;
                } else {
                    return ClickAction.HANDLED; // Deny
                }
            } else {
                return ClickAction.CREATIVE_CLICK_CONSUME;
            }
        }

        // Clicking of next/previous page buttons
        for (UIButton button : buttons) {
            if (button.slot == clickedSlot) {
                button.click();
                return ClickAction.HANDLED;
            }
        }

        // Right click moves back up
        if (isRightClick) {
            ResourcePackModelListing.ListedEntry e = this.current;
            while (e.parent() != null) {
                e = e.parent();
                if (e.compact() == e) {
                    break;
                }
            }
            if (e != this.current) {
                this.setListedEntry(e);
            }
            return ClickAction.HANDLED;
        }

        int offset = this.page * DISPLAYED_ITEM_COUNT;
        int limit = Math.min(DISPLAYED_ITEM_COUNT, currentItems.size() - offset);
        if (clickedSlot < limit) {
            ResourcePackModelListing.ListedEntry e = this.currentItems.get(clickedSlot + offset);
            if (e instanceof ResourcePackModelListing.ListedItemModel) {
                return onItemClicked((ResourcePackModelListing.ListedItemModel) e);
            } else {
                this.setListedEntry(e);
            }
        }

        return ClickAction.HANDLED;
    }

    private void setListedEntry(ResourcePackModelListing.ListedEntry current) {
        this.current = current;
        this.currentItems = current.displayedItems(DISPLAYED_ITEM_COUNT);
        this.page = 0;
        this.updateItems();
    }

    private void incrementPage(int incr) {
        int newPage = page + incr;
        if (newPage < 0) {
            newPage = 0;
        } else {
            int maxPage = this.currentItems.size() / DISPLAYED_ITEM_COUNT;
            if (newPage > maxPage) {
                newPage = maxPage;
            }
        }
        if (newPage != page) {
            this.page = newPage;
            this.updateItems();
        }
    }

    private void updateItemsNextTick() {
        Bukkit.getScheduler().scheduleSyncDelayedTask(options.plugin(), this::updateItems);
    }

    private void updateItems() {
        int offset = page * DISPLAYED_ITEM_COUNT;
        int limit = Math.min(DISPLAYED_ITEM_COUNT, currentItems.size() - offset);

        this.btnPrevPage.enabled = (page > 0);
        this.btnNextPage.enabled = (currentItems.size() - offset) > DISPLAYED_ITEM_COUNT;

        for (int i = 0; i < limit; i++) {
            this.inventory.setItem(i, currentItems.get(i + offset).item().clone());
        }
        for (int i = limit; i < DISPLAYED_ITEM_COUNT; i++) {
            this.inventory.setItem(i, options.bgItem);
        }
        for (UIButton button : buttons) {
            this.inventory.setItem(button.slot, button.item());
        }
        for (int i = DISPLAYED_ITEM_COUNT; i < (9*6); i++) {
            boolean isButtonSlot = false;
            for (UIButton button : buttons) {
                if (button.slot == i) {
                    isButtonSlot = true;
                    break;
                }
            }
            if (!isButtonSlot) {
                this.inventory.setItem(i, options.bgItem);
            }
        }
    }

    private void cancelDialog() {
        CommonUtil.unregisterListener(this);

        // Also de-register from the listing
        {
            ResourcePackModelListingDialog dialog = shownTo.remove(this.player());
            if (dialog != this) {
                shownTo.put(this.player(), dialog);
            }
        }

        // Mark as closed
        future.complete(Optional.empty());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == this.player()) {
            this.cancelDialog();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer() == this.player()) {
            this.close();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() == this.player() && event.getInventory() == this.inventory) {
            cancelDialog();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() != this.player() || event.getInventory() != this.inventory) {
            return;
        }

        // If any slots 0 - 9x6 are involved in this, cancel
        // Do allow dragging in the hotbar / players own inventory
        // If an item is dragged exclusively in this dialog, and it is a creative menu,
        // consume all items entirely.
        // Note: must use RAW slots, as the difference can't be detected otherwise
        long numDraggedInDialog = event.getRawSlots().stream()
                .mapToInt(Integer::intValue)
                .filter(i -> i >= 0 && i < (6*9))
                .count();
        if (options.creativeMenu && numDraggedInDialog == event.getRawSlots().size()) {
            // Consume all the items. Note: setCursor(null) doesn't work with result DENY.
            // For that reason, update the cursor a tick delayed to what it would be
            // had the drag been completed.

            // If the user left-click drags on an item slot with the same item on the cursor
            // as is in the slot, then this behaves identically to increasing the item count
            // on the cursor by one.
            ItemStack cursorAfterTmp = event.getCursor();
            if (!ItemUtil.isEmpty(event.getOldCursor()) &&
                event.getType() == DragType.EVEN && // left click
                numDraggedInDialog == 1 &&
                ItemUtil.equalsIgnoreAmount(
                        ItemUtil.createItem(event.getOldCursor()),
                        this.inventory.getItem(
                                event.getInventorySlots().iterator().next().intValue()))
            ) {
                cursorAfterTmp = event.getOldCursor().clone();
                if (cursorAfterTmp.getAmount() < cursorAfterTmp.getMaxStackSize()) {
                    cursorAfterTmp.setAmount(cursorAfterTmp.getAmount() + 1);
                }
            }

            final ItemStack cursorAfter = ItemUtil.cloneItem(cursorAfterTmp);
            final ItemStack cursorExpected = ItemUtil.cloneItem(event.getOldCursor());
            Bukkit.getScheduler().scheduleSyncDelayedTask(options.plugin(), () -> {
                if (LogicUtil.bothNullOrEqual(player().getItemOnCursor(), cursorExpected)) {
                    player().setItemOnCursor(cursorAfter);
                }
            });
            event.setCursor(cursorAfter);
            event.setResult(Result.DENY);
        } else if (numDraggedInDialog > 0) {
            // Don't allow partial / non-creative dragging in the window
            event.setResult(Result.DENY);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    private void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() != this.player()) {
            return;
        }

        // Disallow moving items into the menu
        // If this is a creative menu, delete the items moved from the inventory
        Inventory clickedInventory = ItemUtil.getClickedInventory(event);
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY &&
            event.getInventory() == this.inventory &&
            clickedInventory != this.inventory
        ) {
            if (options.creativeMenu) {
                event.setResult(Result.DENY);
                event.setCurrentItem(null);
            } else {
                event.setResult(Result.DENY);
            }
            return;
        }

        // If right clicking into the dialog while holding an item, and this is a creative menu,
        // reduce the count by one of the cursor. Until it is fully consumed.
        if ((event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) &&
            options.creativeMenu &&
            !ItemUtil.isEmpty(event.getCursor()) &&
            clickedInventory == this.inventory &&
            event.getSlot() >= 0 && event.getSlot() < (9*6)
        ) {
            if (event.getCursor().getAmount() > 1) {
                ItemStack updated = event.getCursor().clone();
                updated.setAmount(updated.getAmount() - 1);
                event.setCursor(updated);
                event.setResult(Result.DENY);
            } else {
                event.setCursor(null);
                event.setResult(Result.DENY);
            }
            return;
        }

        // If player is trying to collect all of the same item, this might mess
        // with the inventory itself if an item from there is taken.
        // For the duration of handling this event, set the slots affected to null
        // and refresh them again next tick. This avoids those slots being considered
        // in the collect-all calculations.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR &&
            clickedInventory != this.inventory &&
            !ItemUtil.isEmpty(event.getCursor())
        ) {
            // Check any of the items match with the one taken
            boolean hasItemThatMatches = false;
            ItemStack match = ItemUtil.createItem(event.getCursor());
            for (int i = 0; i < (9*6); i++) {
                ItemStack invItem = this.inventory.getItem(i);
                if (invItem != null && ItemUtil.equalsIgnoreAmount(invItem, match)) {
                    hasItemThatMatches = true;
                    this.inventory.setItem(i, null);
                }
            }
            if (hasItemThatMatches) {
                updateItemsNextTick();
            }
        }

        // Allow stuff that happens in the player's own inventory
        if (clickedInventory != this.inventory) {
            return;
        }

        boolean isRightClick = (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT);
        ClickAction action = handleClick(event.getSlot(), isRightClick, event.getCursor());
        switch (action) {
        case CREATIVE_CLICK_CONSUME:
            event.setCursor(null);
            event.setResult(Result.DENY);
            break;
        case CREATIVE_CLICK_PICKUP:
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                // Let the default inventory logic take care of this one
                // Refresh items next tick to repopulate the UI
                event.setResult(Result.ALLOW);
                updateItemsNextTick();
            } else if (event.getCurrentItem() != null) {
                // Put item on the cursor
                ItemStack pickedItem = event.getCurrentItem().clone();
                if (event.getAction() == InventoryAction.CLONE_STACK) {
                    pickedItem.setAmount(pickedItem.getMaxStackSize());
                }
                event.setCursor(pickedItem);
                event.setResult(Result.DENY);
            } else {
                // Glitched?
                updateItemsNextTick();
                event.setCursor(null);
                event.setResult(Result.DENY);
            }
            break;
        case CREATIVE_CLICK_INCREASE_COUNT:
            ItemStack incrItem = event.getCursor().clone();
            incrItem.setAmount(incrItem.getAmount() + 1);
            event.setCursor(incrItem);
            event.setResult(Result.DENY);
            break;
        case HANDLED:
            event.setResult(Result.DENY);
            break;
        default:
            break;
        }
    }

    private static enum ClickAction {
        /** Action was handled by the dialog, and event should be cancelled */
        HANDLED,
        /** Action was on an item model and the creative click pick-up item behavior should happen */
        CREATIVE_CLICK_PICKUP,
        /** Action was on the menu, and it consumes (deletes) the item like in creative */
        CREATIVE_CLICK_CONSUME,
        /** Action was on an item model that matches the item held. Increase count of item held by one */
        CREATIVE_CLICK_INCREASE_COUNT
    }

    private class PrevPageButton extends UIButton {
        private final ItemStack enabledIconItem = createItem(item -> {
            ItemUtil.setDisplayName(item, ChatColor.GREEN + "Previous Page");
        }, "DIAMOND_BLOCK", "LEGACY_DIAMOND_BLOCK");
        private final ItemStack disabledIconItem = createItem(item -> {
            ItemUtil.setDisplayName(item, ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + "Previous Page");
        }, "CLAY", "LEGACY_CLAY");

        public PrevPageButton() {
            super(5 * 9);
        }

        @Override
        public ItemStack item() {
            return enabled ? enabledIconItem : disabledIconItem;
        }

        @Override
        public void click() {
            incrementPage(-1);
        }
    }

    private class NextPageButton extends UIButton {
        private final ItemStack enabledIconItem = createItem(item -> {
            ItemUtil.setDisplayName(item, ChatColor.GREEN + "Next Page");
        }, "DIAMOND_BLOCK", "LEGACY_DIAMOND_BLOCK");
        private final ItemStack disabledIconItem = createItem(item -> {
            ItemUtil.setDisplayName(item, ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + "Next Page");
        }, "CLAY", "LEGACY_CLAY");

        public NextPageButton() {
            super(5 * 9 + 8);
        }

        @Override
        public ItemStack item() {
            return enabled ? enabledIconItem : disabledIconItem;
        }

        @Override
        public void click() {
            incrementPage(1);
        }
    }

    private class SearchButton extends UIButton {
        private final ItemStack searchIconItem = createItem(item -> {
            ItemUtil.setDisplayName(item, ChatColor.YELLOW + "Enter search query");
        }, "COMPASS", "LEGACY_COMPASS");

        public SearchButton() {
            super(5 * 9 + 4);
        }

        @Override
        public ItemStack item() {
            return searchIconItem;
        }

        @Override
        public void click() {
        }
    }

    private static ItemStack createItem(Consumer<ItemStack> setup, String... materialNames) {
        ItemStack item = ItemUtil.createItem(MaterialUtil.getFirst(materialNames), 1);
        setup.accept(item);
        return item;
    }

    private static abstract class UIButton {
        public final int slot;
        public boolean enabled = true;

        public UIButton(int slot) {
            this.slot = slot;
        }

        public abstract ItemStack item();
        public abstract void click();
    }
}
