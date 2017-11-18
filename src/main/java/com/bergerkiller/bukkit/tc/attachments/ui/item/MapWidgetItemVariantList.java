package com.bergerkiller.bukkit.tc.attachments.ui.item;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapResourcePack;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.CommonSounds;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;

/**
 * Interactive widget that pops down a full list of item base types when
 * activated, and allows switching between item/block variants using left/right.
 */
public abstract class MapWidgetItemVariantList extends MapWidget {
    private final MapTexture background;
    private List<ItemStack> variants;
    private int variantIndex = 0;

    public MapWidgetItemVariantList() {
        this.background = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/item_selector_bg.png");
        this.setSize(86, 18);
        this.setFocusable(true);
        this.variants = new ArrayList<ItemStack>(0);
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
            this.onItemChanged();
            return;
        }
        int maxDurability = ItemUtil.getMaxDurability(item);
        if (maxDurability > 0) {
            // Uses durability
            this.variants = new ArrayList<ItemStack>(maxDurability);
            for (int i = 0; i < maxDurability; i++) {
                ItemStack tmp = item.clone();
                tmp.setDurability((short) i);
                this.variants.add(tmp);
            }
        } else {
            // Find variants using internal lookup (creative menu)
            this.variants = ItemUtil.getItemVariants(item.getType());
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

        // Make unbreakable
        for (int i = 0; i < this.variants.size(); i++) {
            ItemStack variant = ItemUtil.createItem(this.variants.get(i));
            ItemUtil.getMetaTag(variant, true).putValue("Unbreakable", true);
            this.variants.set(i, variant);
        }
        this.invalidate();
        this.onItemChanged();
    }

    @Override
    public void onDraw() {
        // Background
        this.view.draw(this.background, 0, 0);

        // Draw the same item with -2 to +2 variant indices
        int x = 1;
        int y = 1;
        for (int index = this.variantIndex - 2; index <= this.variantIndex + 2; index++) {
            // Check index valid
            if (index >= 0 && index < this.variants.size()) {
                view.drawItem(MapResourcePack.SERVER, this.variants.get(index), x, y, 16, 16);
            }
            x += 17;
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.LEFT) {
            if (this.variantIndex > 0) {
                this.variantIndex--;
                this.invalidate();
                this.onItemChanged();
                this.display.playSound(CommonSounds.CLICK);
            }
        } else if (event.getKey() == Key.RIGHT) {
            if (this.variantIndex < (this.variants.size() - 1)) {
                this.variantIndex++;
                this.invalidate();
                this.onItemChanged();
                this.display.playSound(CommonSounds.CLICK);
            }
        } else {
            super.onKeyPressed(event);
        }
    }

    public abstract void onItemChanged();
}
