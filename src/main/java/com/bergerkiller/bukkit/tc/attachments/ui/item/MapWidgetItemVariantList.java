package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetTooltip;
import com.bergerkiller.bukkit.tc.attachments.ui.models.ResourcePackModelListing;
import com.bergerkiller.bukkit.tc.attachments.ui.models.listing.ListedItemModel;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.ParseUtil;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetArrow;
import com.bergerkiller.bukkit.tc.attachments.ui.SetValueTarget;

/**
 * Interactive widget that pops down a full list of item base types when
 * activated, and allows switching between item/block variants using left/right.
 */
public abstract class MapWidgetItemVariantList extends MapWidget implements SetValueTarget, ItemChangedListener {
    private final List<ItemChangedListener> itemChangedListeners = new ArrayList<ItemChangedListener>();
    private final MapWidgetArrow nav_left = new MapWidgetArrow(BlockFace.WEST);
    private final MapWidgetArrow nav_right = new MapWidgetArrow(BlockFace.EAST);
    private final MapWidgetTooltip below_tooltip = new MapWidgetTooltip();
    private final MapTexture background;
    private List<CommonItemStack> variants;
    private Map<CommonItemStack, MapTexture> iconCache = new HashMap<>();
    private int variantIndex = 0;

    public MapWidgetItemVariantList() {
        this.background = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_selector_bg.png");
        this.setSize(100, 18);
        this.setFocusable(true);
        this.variants = new ArrayList<CommonItemStack>(0);

        this.nav_left.setPosition(0, 4);
        this.nav_right.setPosition(this.getWidth() - nav_right.getWidth(), 4);
        this.nav_left.setVisible(false);
        this.nav_right.setVisible(false);
        this.addWidget(this.nav_left);
        this.addWidget(this.nav_right);
        this.addWidget(this.below_tooltip);
        this.setRetainChildWidgets(true);

        this.itemChangedListeners.add(this);
    }

    public CommonItemStack getItem() {
        if (this.variantIndex >= 0 && this.variantIndex < this.variants.size()) {
            return this.variants.get(this.variantIndex);
        } else {
            return CommonItemStack.empty();
        }
    }

    public void setItem(ItemStack item) {
        setItem(CommonItemStack.of(item));
    }

    public void setItem(CommonItemStack item) {
        // Refresh the variants list
        loadVariants(item);

        if (item.isEmpty()) {
            this.invalidate();
            this.fireItemChangeEvent();
            return;
        }

        // Find the item in the variants to deduce the currently selected index
        this.variantIndex = 0;
        for (int i = 0; i < this.variants.size(); i++) {
            CommonItemStack variant = this.variants.get(i);
            if (variant.equalsIgnoreAmount(item)) {
                this.variantIndex = i;
                break; // Final!
            }
            if (item.isDamageSupported() && variant.getDamage() == item.getDamage()) {
                this.variantIndex = i;
            }
        }

        this.invalidate();
        this.fireItemChangeEvent();
    }

    private void loadVariants(CommonItemStack item) {
        // If item is null/empty, ignore
        if (item.isEmpty()) {
            this.variants = new ArrayList<>();
            this.variantIndex = 0;
            return;
        }

        // If item is part of traincarts model listing, make a variant list of item models
        ResourcePackModelListing models = TrainCarts.plugin.getModelListing();
        if (models.isBareItem(item.toBukkit())) {
            this.variants = new ArrayList<>(models.root().bareItemStacks().keySet());
            return;
        }

        // If it used durability, make a listing of all durabilities of this item
        if (item.isDamageSupported()) {
            // Uses durability
            int maxDamage = item.getMaxDamage();
            this.variants = new ArrayList<CommonItemStack>(maxDamage + 1);
            for (int i = 0; i <= maxDamage; i++) {
                this.variants.add(item.clone().setDamage(i));
            }
            return;
        }

        // Find variants using internal lookup (creative menu)
        this.variants = ItemUtil.getItemVariants(item.getType()).stream()
                .filter(Objects::nonNull)
                .map(CommonItemStack::of)
                .map(CommonItemStack::clone)
                .collect(Collectors.toList());

        if (this.variants.size() == 1) {
            // Preserve all properties if there is only one variant
            variants.get(0).toBukkit().setItemMeta(item.toBukkit().getItemMeta());
        } else {
            // Preserve some of the extra properties of the input item
            for (CommonItemStack variant : this.variants) {
                for (Map.Entry<Enchantment, Integer> enchantment : item.toBukkit().getEnchantments().entrySet()) {
                    variant.addEnchantment(enchantment.getKey(), enchantment.getValue().intValue());
                }
            }
            if (item.hasCustomName()) {
                ChatText customName = item.getCustomName();
                for (CommonItemStack variant : this.variants) {
                    variant.setCustomName(customName);
                }
            }
            if (item.isUnbreakable()) {
                for (CommonItemStack variant : this.variants) {
                    variant.setUnbreakable(true);
                }
            }
            if (item.hasCustomModelData()) {
                int customModelData = item.getCustomModelData();
                for (CommonItemStack variant : this.variants) {
                    variant.setCustomModelData(customModelData);
                }
            }
        }
    }

    @Override
    public String getAcceptedPropertyName() {
        return "Item Information";
    }

    @Override
    public boolean acceptTextValue(String value) {
        // Try parsing the item name from the value
        value = value.trim();
        int nameEnd = 0;
        while (nameEnd < value.length()) {
            if (value.charAt(nameEnd) == '{' || value.charAt(nameEnd) == ' ') {
                break;
            } else {
                nameEnd++;
            }
        }
        String itemName = value.substring(0, nameEnd);
        if (nameEnd >= value.length()) {
            value = "";
        } else {
            value = value.substring(nameEnd).trim();
        }
        if (!ParseUtil.isNumeric(itemName)) {
            // Item name
            Material newItemMaterial = ParseUtil.parseMaterial(itemName, null);
            if (newItemMaterial == null) {
                return false;
            }
            CommonItemStack newItem = CommonItemStack.create(newItemMaterial, 1);

            // Item durability
            nameEnd = 0;
            while (nameEnd < value.length()) {
                if (value.charAt(nameEnd) == '{' || value.charAt(nameEnd) == ' ') {
                    break;
                } else {
                    nameEnd++;
                }
            }
            String damageValueStr = value.substring(0, nameEnd).trim();
            if (!damageValueStr.isEmpty() && newItem.isDamageSupported() && ParseUtil.isNumeric(damageValueStr)) {
                try {
                    int damage = Integer.parseInt(damageValueStr);
                    if (damage < 0 || damage > newItem.getMaxDamage()) {
                        return false;
                    }
                    newItem.setDamage(damage);
                } catch (NumberFormatException ex) {
                    return false;
                }
            }

            // Update
            this.setItem(newItem);
        } else {
            // Variant index (no item name specified)
            try {
                this.setVariantIndex(Integer.parseInt(itemName));
            } catch (NumberFormatException ex) {
                return false;
            }
        }

        // Find NBT in Mojangson format
        // TODO: Fix, doesn't work anymore now that components are used
        /*
        int nbtStart = value.indexOf('{');
        if (nbtStart != -1) {
            CommonTagCompound nbt = CommonTagCompound.fromMojangson(value.substring(nbtStart));
            if (nbt == null) {
                return false;
            }
            ItemStack newItem = ItemUtil.createItem(this.getItem());
            short oldDurability = newItem.getDurability();
            ItemUtil.setMetaTag(newItem, nbt);
            if (!nbt.containsKey("Damage")) {
                newItem.setDurability(oldDurability);
            }
            this.setItem(newItem);
        }
        */

        return true;
    }

    @Override
    public void onFocus() {
        nav_left.setVisible(true);
        nav_right.setVisible(true);
    }

    @Override
    public void onBlur() {
        nav_left.setVisible(false);
        nav_right.setVisible(false);
    }

    @Override
    public void onDraw() {
        // Subregion where things are drawn
        // To the left and right are navigation buttons
        int selector_edge = this.nav_left.getWidth()+1;
        MapCanvas itemView = this.view.getView(selector_edge, 0, this.getWidth() - 2*selector_edge, this.getHeight());

        // Background
        itemView.draw(this.background, 0, 0);

        // Draw the same item with -2 to +2 variant indices
        int x = 1;
        int y = 1;
        for (int index = this.variantIndex - 2; index <= this.variantIndex + 2; index++) {
            // Check index valid
            if (index >= 0 && index < this.variants.size()) {
                CommonItemStack item = this.variants.get(index);
                MapTexture icon = this.iconCache.get(item);
                if (icon == null) {
                    icon = MapTexture.createEmpty(16, 16);
                    icon.fillItem(TCConfig.resourcePack, item.toBukkit());
                    this.iconCache.put(item, icon);
                }
                itemView.draw(icon, x, y);
            }
            x += 17;
        }

        // If variants are based on durability, show durability value
        if (this.variantIndex >= 0 && this.variantIndex < this.variants.size()) {
            CommonItemStack item = this.variants.get(this.variantIndex);
            if (item.isDamageSupported()) {
                itemView.setAlignment(MapFont.Alignment.MIDDLE);
                itemView.draw(MapFont.TINY, 44, 12, MapColorPalette.COLOR_RED, Integer.toString(item.getDamage()));
            }
        }
    }

    private void changeVariantIndex(int offset) {
        this.setVariantIndex(this.variantIndex + offset);
    }

    private void setVariantIndex(int newVariantIndex) {
        if (newVariantIndex < 0) {
            newVariantIndex = 0;
        } else if (newVariantIndex >= this.variants.size()) {
            newVariantIndex = this.variants.size()-1;
        }
        if (this.variantIndex == newVariantIndex) {
            return;
        }
        this.variantIndex = newVariantIndex;
        this.invalidate();
        this.fireItemChangeEvent();
        this.display.playSound(SoundEffect.CLICK);
    }

    @Override
    public void onKeyReleased(MapKeyEvent event) {
        super.onKeyReleased(event);
        if (event.getKey() == MapPlayerInput.Key.LEFT) {
            nav_left.stopFocus();
        } else if (event.getKey() == MapPlayerInput.Key.RIGHT) {
            nav_right.stopFocus();
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.LEFT) {
            changeVariantIndex(-1 - (event.getRepeat() / 40));
            nav_left.sendFocus();
        } else if (event.getKey() == Key.RIGHT) {
            changeVariantIndex(1 + (event.getRepeat() / 40));
            nav_right.sendFocus();
        } else {
            super.onKeyPressed(event);
        }
    }

    /**
     * Registers a listener called when the item is changed.
     * 
     * @param listener
     * @param fireEventNow when true, fires an item change event right now while registering
     */
    public void registerItemChangedListener(ItemChangedListener listener, boolean fireEventNow) {
        this.itemChangedListeners.add(listener);
        if (fireEventNow) {
            listener.onItemChanged(this.getItem());
        }
    }

    private void fireItemChangeEvent() {
        CommonItemStack item = this.getItem();

        // Update tooltip
        {
            ListedItemModel itemMeta = TrainCarts.plugin.getModelListing().getBareItemModel(item.toBukkit());
            if (itemMeta != null) {
                below_tooltip.setText(itemMeta.name());
                below_tooltip.setVisible(true);
            } else {
                below_tooltip.setText("");
                below_tooltip.setVisible(false);
            }
        }

        for (ItemChangedListener listener : this.itemChangedListeners) {
            listener.onItemChanged(item);
        }
    }

    @Override
    public void onItemChanged(CommonItemStack item) {}
}
