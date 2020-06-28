package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.bergerkiller.bukkit.common.nbt.CommonTagCompound;
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
    private final MapTexture background;
    private List<ItemStack> variants;
    private Map<ItemStack, MapTexture> iconCache = new HashMap<ItemStack, MapTexture>();
    private int variantIndex = 0;

    public MapWidgetItemVariantList() {
        this.background = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_selector_bg.png");
        this.setSize(100, 18);
        this.setFocusable(true);
        this.variants = new ArrayList<ItemStack>(0);

        this.nav_left.setPosition(0, 4);
        this.nav_right.setPosition(this.getWidth() - nav_right.getWidth(), 4);
        this.nav_left.setVisible(false);
        this.nav_right.setVisible(false);
        this.addWidget(this.nav_left);
        this.addWidget(this.nav_right);
        this.setRetainChildWidgets(true);

        this.itemChangedListeners.add(this);
    }

    public ItemStack getItem() {
        if (this.variantIndex >= 0 && this.variantIndex < this.variants.size()) {
            return this.variants.get(this.variantIndex);
        } else {
            return null;
        }
    }

    public void setItem(ItemStack item) {
        if (item == null) {
            this.variants = new ArrayList<ItemStack>(0);
            this.variantIndex = 0;
            this.invalidate();
            this.fireItemChangeEvent();
            return;
        }
        int maxDurability = ItemUtil.getMaxDurability(item);
        if (maxDurability > 0) {
            // Uses durability
            this.variants = new ArrayList<ItemStack>(maxDurability);
            for (int i = 0; i <= maxDurability; i++) {
                ItemStack tmp = ItemUtil.createItem(item.clone());
                tmp.setDurability((short) i);
                this.variants.add(tmp);
            }
        } else {
            // Find variants using internal lookup (creative menu)
            this.variants = ItemUtil.getItemVariants(item.getType());

            // Guarantee CraftItemStack
            for (int i = 0; i < this.variants.size(); i++) {
                this.variants.set(i, ItemUtil.createItem(this.variants.get(i)));
            }

            if (this.variants.size() == 1) {
                // Preverve all properties if there is only one variant
                variants.get(0).setItemMeta(item.getItemMeta());
            } else {
                // Preserve some of the extra properties of the input item
                for (ItemStack variant : this.variants) {
                    for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
                        variant.addEnchantment(enchantment.getKey(), enchantment.getValue().intValue());
                    }
                }
                if (item.getItemMeta().hasDisplayName()) {
                    String name = item.getItemMeta().getDisplayName();
                    for (ItemStack variant : this.variants) {
                        ItemUtil.setDisplayName(variant, name);
                    }
                }
                CommonTagCompound tag = ItemUtil.getMetaTag(item);
                if (tag != null && tag.containsKey("Unbreakable") && tag.getValue("Unbreakable", false)) {
                    for (ItemStack variant : this.variants) {
                        ItemUtil.getMetaTag(variant, true).putValue("Unbreakable", true);
                    }
                }
                if (tag != null && tag.containsKey("CustomModelData")) {
                    int customModelData = tag.getValue("CustomModelData", 0);
                    if (customModelData > 0) {
                        for (ItemStack variant : this.variants) {
                            ItemUtil.getMetaTag(variant, true).putValue("CustomModelData", customModelData);
                        }
                    }
                }
            }
        }

        // Find the item in the variants to deduce the currently selected index
        this.variantIndex = 0;
        for (int i = 0; i < this.variants.size(); i++) {
            ItemStack variant = this.variants.get(i);
            if (variant.isSimilar(item)) {
                this.variantIndex = i;
                break; // Final!
            }
            if (variant.getDurability() == item.getDurability()) {
                this.variantIndex = i;
            }
        }

        this.invalidate();
        this.fireItemChangeEvent();
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
            ItemStack newItem = ItemUtil.createItem(newItemMaterial, 1);

            // Item durability
            nameEnd = 0;
            while (nameEnd < value.length()) {
                if (value.charAt(nameEnd) == '{' || value.charAt(nameEnd) == ' ') {
                    break;
                } else {
                    nameEnd++;
                }
            }
            String durabilityValueStr = value.substring(0, nameEnd).trim();
            if (!durabilityValueStr.isEmpty() && ParseUtil.isNumeric(durabilityValueStr)) {
                try {
                    int durability = Integer.parseInt(durabilityValueStr);
                    if (durability < 0 || durability > ItemUtil.getMaxDurability(newItem)) {
                        return false;
                    }
                    newItem.setDurability((short) durability);
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
                ItemStack item = this.variants.get(index);
                MapTexture icon = this.iconCache.get(item);
                if (icon == null) {
                    icon = MapTexture.createEmpty(16, 16);
                    icon.fillItem(TCConfig.resourcePack, item);
                    this.iconCache.put(item, icon);
                }
                itemView.draw(icon, x, y);
            }
            x += 17;
        }

        // If variants are based on durability, show durability value
        if (this.variantIndex >= 0 && this.variantIndex < this.variants.size()) {
            ItemStack item = this.variants.get(this.variantIndex);
            if (ItemUtil.getMaxDurability(item) > 0) {
                itemView.setAlignment(MapFont.Alignment.MIDDLE);
                itemView.draw(MapFont.TINY, 44, 12, MapColorPalette.COLOR_RED, Short.toString(item.getDurability()));
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
        ItemStack item = this.getItem();
        for (ItemChangedListener listener : this.itemChangedListeners) {
            listener.onItemChanged(item);
        }
    }

    public void onItemChanged(ItemStack item) {}
}
