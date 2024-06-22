package com.bergerkiller.bukkit.tc.attachments.ui.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
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

import com.bergerkiller.bukkit.common.block.InputDialogSubmitText;
import com.bergerkiller.bukkit.common.internal.CommonCapabilities;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;

import com.bergerkiller.bukkit.tc.attachments.ui.models.listing.*;

/**
 * Displays the contents of {@link ResourcePackModelListing} in an inventory menu dialog
 * for a single player.
 */
class ResourcePackModelListingDialog implements Listener {
    private static final int DISPLAYED_ITEM_COUNT = 4 * 9; // Top 4 rows
    private static final CommonItemStack BG_ITEM1 = CommonItemStack.empty(); // createGlassPaneItem(DyeColor.GRAY);
    private static final CommonItemStack BG_ITEM2 = createGlassPaneItem(DyeColor.BROWN);
    private static final CommonItemStack BG_ITEM3 = createGlassPaneItem(DyeColor.GRAY);
    private static Map<Player, ResourcePackModelListingDialog> shownTo = new HashMap<>();
    private final DialogBuilder options;
    private final CompletableFuture<DialogResult> future;
    private final UIButton btnPrevPage = new PrevPageButton();
    private final UIButton btnBetweenpages = new BetweenPageButton();
    private final UIButton btnNextPage = new NextPageButton();
    private final UIButton btnBack = new BackButton();
    private final UIButton btnSearch = new SearchButton();
    private final List<UIButton> buttons = Arrays.asList(btnPrevPage, btnBetweenpages, btnNextPage, btnBack, btnSearch);
    private Inventory inventory;
    private ResourcePackModelListing currentListing;
    private ListedEntry current;
    private List<? extends ListedEntry> currentItems;
    private boolean futureDisabled = false;

    public static CompletableFuture<DialogResult> show(
            DialogBuilder dialogOptions
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

    private ResourcePackModelListingDialog(DialogBuilder options) {
        this(options, new CompletableFuture<>());
    }

    private ResourcePackModelListingDialog(
            final DialogBuilder options,
            final CompletableFuture<DialogResult> future
    ) {
        this.options = options;
        this.future = future;
    }

    public void open() {
        if (options.getQuery().isEmpty()) {
            currentListing = options.listing();
        } else {
            currentListing = options.listing().filter(options.getQuery());
        }

        Bukkit.getPluginManager().registerEvents(this, options.plugin());

        inventory = Bukkit.createInventory(player(), 54, options.getTitle());

        // Find the entry using the options browsed path
        // If not found, reset back to root
        ListedEntry initialEntry = currentListing.root().findAtPath(ListedEntry.tokenizePath(options.getBrowsedPath()))
                .orElse(currentListing.root())
                .compactIf(options.isCompactingEnabled());
        navigate(initialEntry, options.getBrowsedPage());

        player().openInventory(inventory);
    }

    public void close() {
        cancelDialog(false);
        if (player().getOpenInventory() != null && player().getOpenInventory().getTopInventory() == this.inventory) {
            player().closeInventory();
        }
    }

    public void closeAndShowSearchDialog(String initialQuery) {
        // Disable future so when the window is closed, it doesn't get completed
        // This dialog instance becomes invalid once the anvil dialog is shown
        futureDisabled = true;
        close();

        final DialogBuilder newOptions = options.clone();
        final CompletableFuture<DialogResult> future = this.future;
        new InputDialogSubmitText(options.plugin(), options.player()) {
            @Override
            public void onAccept(String text) {
                newOptions.query(text);
                newOptions.navigate("", 0);
                newOptions.show().thenAccept(future::complete);
            }

            @Override
            public void onCancel() {
                newOptions.show().thenAccept(future::complete);
            }
        }.setDescription("Enter search query")
         .setInitialText(newOptions.getQuery())
         .setAcceptEmptyText(true)
         .open();
    }

    private Player player() {
        return options.player();
    }

    private void complete(DialogResult result) {
        if (!futureDisabled) {
            future.complete(result);
        }
    }

    private ClickAction onItemClicked(ListedItemModel item) {
        if (options.isCreativeMenu()) {
            return ClickAction.CREATIVE_CLICK_PICKUP;
        }

        // Accept this item and close the dialog
        complete(new DialogResult(options, item));
        return ClickAction.CLOSE_DIALOG;
    }

    private boolean tryNavigateBack(boolean toRoot) {
        ListedEntry e = this.current;
        if (toRoot) {
            while (e.parent() != null) {
                e = e.parent();
            }
            e = e.compactIf(options.isCompactingEnabled());
        } else {
            while (e.parent() != null) {
                e = e.parent();
                if (e.compact() == e) {
                    break;
                }
            }
        }
        if (e != this.current) {
            this.navigate(e, 0);
            return true;
        } else {
            return false;
        }
    }

    private ClickAction handleClick(int clickedSlot, boolean isRightClick, ItemStack cursorItem) {
        // If this is a creative menu and a cursor item is held, allow dropping (deleting) the item
        // like a traditional creative menu would do.
        // If the cursor item matches with the item held in this slot in this inventory (except count),
        // allow increasing the count by one until the stack size is reached.
        if (options.isCreativeMenu() && !ItemUtil.isEmpty(cursorItem) && clickedSlot >= 0 && clickedSlot < (6*9)) {
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
                button.click(isRightClick);
                return ClickAction.HANDLED;
            }
        }

        // Right click moves back up
        if (isRightClick) {
            if (!tryNavigateBack(false) && options.isCancelOnRootRightClick()) {
                complete(new DialogResult(options, true));
                return ClickAction.CLOSE_DIALOG;
            }
            return ClickAction.HANDLED;
        }

        int offset = options.getBrowsedPage() * DISPLAYED_ITEM_COUNT;
        int limit = Math.min(DISPLAYED_ITEM_COUNT, currentItems.size() - offset);
        if (clickedSlot < limit) {
            ListedEntry e = this.currentItems.get(clickedSlot + offset);
            if (e instanceof ListedItemModel) {
                return onItemClicked((ListedItemModel) e);
            } else {
                this.navigate(e, 0);
            }
        }

        return ClickAction.HANDLED;
    }

    private void navigate(ListedEntry current, int page) {
        this.current = current;
        this.currentItems = current.displayedItems(DISPLAYED_ITEM_COUNT, options.isCompactingEnabled());
        this.options.navigate(current.fullPath(), clampPage(page)); // Update for dialog result
        this.updateItems();
    }

    private void incrementPage(int incr) {
        int newPage = clampPage(options.getBrowsedPage() + incr);
        if (newPage != options.getBrowsedPage()) {
            this.options.navigate(current.fullPath(), newPage);
            this.updateItems();
        }
    }

    private int clampPage(int newPage) {
        if (newPage < 0) {
            return 0;
        } else {
            return Math.min(newPage, this.currentItems.size() / DISPLAYED_ITEM_COUNT);
        }
    }

    private void updateItemsNextTick() {
        Bukkit.getScheduler().scheduleSyncDelayedTask(options.plugin(), this::updateItems);
    }

    private void updateItems() {
        int page = options.getBrowsedPage();
        int offset = page * DISPLAYED_ITEM_COUNT;
        int limit = Math.min(DISPLAYED_ITEM_COUNT, currentItems.size() - offset);

        this.btnPrevPage.enabled = (page > 0);
        this.btnNextPage.enabled = (currentItems.size() - offset) > DISPLAYED_ITEM_COUNT;

        for (int i = 0; i < limit; i++) {
            this.inventory.setItem(i, currentItems.get(i + offset).createIconItem(options).toBukkit());
        }
        for (int i = limit; i < DISPLAYED_ITEM_COUNT; i++) {
            this.inventory.setItem(i, BG_ITEM1.toBukkit());
        }

        // Dividing line
        for (int i = DISPLAYED_ITEM_COUNT; i < (9*5); i++) {
            this.inventory.setItem(i, BG_ITEM2.toBukkit());
        }

        // UI Buttons
        for (UIButton button : buttons) {
            this.inventory.setItem(button.slot, button.item().toBukkit());
        }
        for (int i = (9*5); i < (9*6); i++) {
            boolean isButtonSlot = false;
            for (UIButton button : buttons) {
                if (button.slot == i) {
                    isButtonSlot = true;
                    break;
                }
            }
            if (!isButtonSlot) {
                this.inventory.setItem(i, BG_ITEM3.toBukkit());
            }
        }
    }

    private void cancelDialog(boolean delayEvent) {
        CommonUtil.unregisterListener(this);

        // Also de-register from the listing
        {
            ResourcePackModelListingDialog dialog = shownTo.remove(this.player());
            if (dialog != null && dialog != this) {
                shownTo.put(this.player(), dialog);
            }
        }

        // Mark as closed
        final DialogResult result = new DialogResult(options, false);
        if (delayEvent) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(options.plugin(), () -> complete(result));
        } else {
            complete(result);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() == this.player()) {
            this.cancelDialog(false);
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
            cancelDialog(true);
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
        if (options.isCreativeMenu() && numDraggedInDialog == event.getRawSlots().size()) {
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
            if (options.isCreativeMenu()) {
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
            options.isCreativeMenu() &&
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
        case CLOSE_DIALOG:
            event.setResult(Result.DENY);
            close();
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
        /** Dialog should be closed now */
        CLOSE_DIALOG,
        /** Action was on an item model and the creative click pick-up item behavior should happen */
        CREATIVE_CLICK_PICKUP,
        /** Action was on the menu, and it consumes (deletes) the item like in creative */
        CREATIVE_CLICK_CONSUME,
        /** Action was on an item model that matches the item held. Increase count of item held by one */
        CREATIVE_CLICK_INCREASE_COUNT
    }

    private class PrevPageButton extends UIButton {
        private final CommonItemStack enabledIconItem = createItem("DIAMOND_BLOCK", "LEGACY_DIAMOND_BLOCK")
                .setCustomNameMessage(ChatColor.GREEN + "Previous Page");
        private final CommonItemStack disabledIconItem = createItem("CLAY", "LEGACY_CLAY")
                .setCustomNameMessage(ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + "Previous Page");

        public PrevPageButton() {
            super(3);
        }

        @Override
        public CommonItemStack item() {
            return applyPageInfo(enabled ? enabledIconItem : disabledIconItem, false);
        }

        @Override
        public void click(boolean isRightClick) {
            incrementPage(-1);
        }
    }

    private class NextPageButton extends UIButton {
        private final CommonItemStack enabledIconItem = createItem("DIAMOND_BLOCK", "LEGACY_DIAMOND_BLOCK")
                .setCustomNameMessage(ChatColor.GREEN + "Next Page");
        private final CommonItemStack disabledIconItem = createItem( "CLAY", "LEGACY_CLAY")
                .setCustomNameMessage(ChatColor.GRAY.toString() + ChatColor.STRIKETHROUGH + "Next Page");

        public NextPageButton() {
            super(5);
        }

        @Override
        public CommonItemStack item() {
            return applyPageInfo(enabled ? enabledIconItem : disabledIconItem, false);
        }

        @Override
        public void click(boolean isRightClick) {
            incrementPage(1);
        }
    }

    private class BetweenPageButton extends UIButton {

        public BetweenPageButton() {
            super(4);
        }

        @Override
        public CommonItemStack item() {
            return applyPageInfo(BG_ITEM3, true);
        }

        @Override
        public void click(boolean isRightClick) {
        }
    }

    private class BackButton extends UIButton {
        private final CommonItemStack backIconItem = createItem("BOOK", "LEGACY_BOOK")
                .setCustomNameMessage(ChatColor.YELLOW + "Back")
                .addLoreLine()
                .addLoreMessage(ChatColor.BLUE.toString() + ChatColor.ITALIC +
                        "Right-click to go")
                .addLoreMessage(ChatColor.BLUE.toString() + ChatColor.ITALIC +
                        "all the way back");

        public BackButton() {
            super(0);
        }

        @Override
        public CommonItemStack item() {
            return backIconItem;
        }

        @Override
        public void click(boolean isRightClick) {
            // Right-clicking navigates to root without ever closing the dialog
            // Left-clicking past root and if the option for it is enabled, closes the dialog
            if (!tryNavigateBack(isRightClick) && !isRightClick && options.isCancelOnRootRightClick()) {
                complete(new DialogResult(options, true));
                close();
            }
        }
    }

    private class SearchButton extends UIButton {
        private final CommonItemStack searchIconItem = createItem("COMPASS", "LEGACY_COMPASS")
                .setCustomNameMessage(ChatColor.YELLOW + "Enter search query");

        public SearchButton() {
            super(8);
        }

        @Override
        public CommonItemStack item() {
            CommonItemStack item = searchIconItem.clone();
            if (!options.getQuery().isEmpty()) {
                item.addLoreLine()
                    .addLoreMessage(ChatColor.DARK_GRAY + "Current: " +
                        ChatColor.GRAY + ChatColor.ITALIC + "\"" + options.getQuery() + "\"")
                    .addLoreMessage(ChatColor.BLUE.toString() + ChatColor.ITALIC +
                        "Right-click to clear");
            }
            return item;
        }

        @Override
        public void click(boolean isRightClick) {
            if (isRightClick) {
                // If a query was set, reset it and reshow from root
                options.query("");
                navigate(options.listing().root().compactIf(options.isCompactingEnabled()), 0);
            } else {
                closeAndShowSearchDialog(options.getQuery());
            }
        }
    }

    private CommonItemStack applyPageInfo(CommonItemStack item, boolean isMiddleCountItem) {
        // Empty check
        if (item.isEmpty()) {
            return CommonItemStack.empty();
        }

        // If only one page exists, omit the count
        int pageCount = 1 + (currentItems.size() / DISPLAYED_ITEM_COUNT);
        if (pageCount == 1) {
            return item;
        }

        item = item.clone();
        int currPage = (options.getBrowsedPage() + 1);
        if (isMiddleCountItem) {
            item.setCustomNameMessage(ChatColor.DARK_GRAY + "Currently on");
            if (currPage <= 64) {
                item.setAmount(currPage);
            }
        } else {
            item.addLoreLine()
                .addLoreMessage(ChatColor.DARK_GRAY + "Currently on");
        }
        item.addLoreMessage(ChatColor.DARK_GRAY + "page " +
                ChatColor.GRAY + currPage + ChatColor.DARK_GRAY +
                " of " + ChatColor.GRAY + pageCount);
        return item;
    }

    private static CommonItemStack createItem(String... materialNames) {
        return CommonItemStack.create(MaterialUtil.getFirst(materialNames), 1);
    }

    @SuppressWarnings("deprecation")
    private static CommonItemStack createGlassPaneItem(DyeColor color) {
        try {
            CommonItemStack item;
            if (CommonCapabilities.MATERIAL_ENUM_CHANGES) {
                item = CommonItemStack.create(
                        MaterialUtil.getMaterial(color.name() + "_STAINED_GLASS_PANE"),
                        1);
            } else {
                item = BlockData.fromMaterialData(
                            MaterialUtil.getMaterial("LEGACY_STAINED_GLASS_PANE"),
                            color.getWoolData()
                       ).createCommonItem(1);
            }
            return item.setEmptyCustomName();
        } catch (Throwable t) {
            return null; // Meh. Not important enough to fail everything!
        }
    }

    private static abstract class UIButton {
        public final int slot;
        public boolean enabled = true;

        public UIButton(int slot) {
            this.slot = (5 * 9) + slot;
        }

        public abstract CommonItemStack item();
        public abstract void click(boolean isRightClick);
    }
}
