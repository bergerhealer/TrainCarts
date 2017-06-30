package com.bergerkiller.bukkit.tc.editor;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Util;

public class TCMapEditor extends MapDisplay {
    Player owner; // only one player will ever control an edit session
    MapTexture background;
    Sign sign = null;

    @Override
    public void onAttached() {
        this.setSessionMode(MapSessionMode.VIEWING);
        this.setReceiveInputWhenHolding(true);
        this.owner = this.getOwners().get(0);

        List<Block> signBlocks = new ArrayList<Block>();
        BlockLocation searchLocation = ItemUtil.getMetaTag(this.getMapItem()).getBlockLocation("selected");
        if (searchLocation != null) {
            Block searchBlock = searchLocation.getBlock();
            if (MaterialUtil.ISSIGN.get(searchBlock)) {
                signBlocks.add(searchBlock);
            } else {
                Util.getSignsFromRails(signBlocks, searchBlock);
            }
        }
        if (!signBlocks.isEmpty()) {
            sign = BlockUtil.getSign(signBlocks.get(0));
        }

        background = this.loadTexture("com/bergerkiller/bukkit/tc/textures/background.png");
        this.getLayer().draw(background, 0, 0);

        if (sign == null) {
            this.getLayer(1).draw(MapFont.MINECRAFT, 20, 30, MapColorPalette.getColor(255, 0, 0), "No sign selected");
        } else {
            for (int i = 0; i < 4; i++) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 20, 20 + i * 10, MapColorPalette.getColor(0, 0, 255), sign.getLine(i));
            }
        }
    }

    @Override
    public void onDetached() {
        TCMapControl.updateMapItem(this.owner, this.getMapItem(), false);
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.BACK) {
            TCMapControl.updateMapItem(event.getPlayer(), this.getMapItem(), false);
        }
    }

    @Override
    public void onTick() {

    }
}
