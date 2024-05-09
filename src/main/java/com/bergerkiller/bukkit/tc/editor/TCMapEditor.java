package com.bergerkiller.bukkit.tc.editor;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Effect;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.BlockLocation;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapBlendMode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapFont.Alignment;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.BlockUtil;
import com.bergerkiller.bukkit.common.utils.MaterialUtil;
import com.bergerkiller.bukkit.tc.Util;

public class TCMapEditor extends MapDisplay {
    Player owner; // only one player will ever control an edit session
    MapTexture background;
    EditedSign sign = new EditedSign();
    ArrayList<MapControl> controls = new ArrayList<MapControl>();
    int selectedIndex = 0;
    RailsTexture texture;
    Block railsBlock = null;

    public Block getRailsBlock() {
        return this.railsBlock;
    }

    public Player getOwner() {
        return this.owner;
    }

    public void addControl(MapControl control) {
        this.controls.add(control);
        if (this.controls.size() == 1) {
            control.setSelected(true);
            this.selectedIndex = 0;
        }
        control.bind(this);
    }

    @Override
    public void onAttached() {
        this.setGlobal(false);
        this.setSessionMode(MapSessionMode.VIEWING);
        this.setReceiveInputWhenHolding(true);
        this.owner = this.getOwners().get(0);

        this.texture = new RailsTexture();
        List<Block> signBlocks = new ArrayList<Block>();
        BlockLocation searchLocation = this.getCommonMapItem().getCustomData().getBlockLocation("selected");
        if (searchLocation != null) {
            Block searchBlock = searchLocation.getBlock();
            this.railsBlock = searchBlock;
            if (MaterialUtil.ISSIGN.get(searchBlock)) {
                signBlocks.add(searchBlock);
                this.railsBlock = Util.getRailsFromSign(searchBlock);
            } else {
                Util.getSignsFromRails(signBlocks, searchBlock);
            }
        }
        if (!signBlocks.isEmpty()) {
            sign.load(BlockUtil.getSign(signBlocks.get(0)));
        }

        background = this.loadTexture("com/bergerkiller/bukkit/tc/textures/background.png");
        this.getLayer().setBlendMode(MapBlendMode.NONE);
        this.getLayer().draw(background, 0, 0);

        if (sign.isValid()) {
            this.getLayer(1).setBlendMode(MapBlendMode.NONE);
            this.getLayer(1).setAlignment(Alignment.MIDDLE);
            this.getLayer(1).draw(MapFont.MINECRAFT, 64, 5, MapColorPalette.getColor(255, 0, 0), sign.getName());

            sign.initEditor(this);
        } else {
            this.getLayer(1).setBlendMode(MapBlendMode.NONE);
            this.getLayer(1).setAlignment(Alignment.MIDDLE);
            this.getLayer(1).draw(MapFont.MINECRAFT, 64, 5, MapColorPalette.getColor(255, 0, 0), "No sign selected");
        }
        
    }

    public void playClick() {
        this.owner.getWorld().playEffect(this.owner.getLocation(), Effect.CLICK2, 0);
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
        if (controls.size() > 1) {
            if (event.getKey() == Key.LEFT) {
                controls.get(selectedIndex).setSelected(false);
                if (--selectedIndex < 0) {
                    selectedIndex = controls.size() - 1;
                }
                controls.get(selectedIndex).setSelected(true);
                playClick();
            } else if (event.getKey() == Key.RIGHT) {
                controls.get(selectedIndex).setSelected(false);
                if (++selectedIndex >= controls.size()) {
                    selectedIndex = 0;
                }
                controls.get(selectedIndex).setSelected(true);
                playClick();
            }
        }
        if (controls.size() > 0) {
            if (event.getKey() == Key.DOWN || event.getKey() == Key.UP || event.getKey() == Key.ENTER) {
                controls.get(selectedIndex).onKeyPressed(event);
                playClick();
            }
        }
    }

    @Override
    public void onTick() {
        for (MapControl control : this.controls) {
            control.onTick();
        }
    }
}
