package com.bergerkiller.bukkit.tc.tickets;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.ItemUtil;

public class TCTicketDisplay extends MapDisplay {

    @Override
    public void onAttached() {
        this.setSessionMode(MapSessionMode.VIEWING);
        this.setGlobal(false);

        renderBackground();
        renderTicket();
    }

    @Override
    public void onMapItemChanged() {
        this.renderTicket();
    }

    public void renderBackground() {
        Ticket ticket = TicketStore.getTicketFromItem(this.getMapItem());
        MapTexture bg;
        if (ticket == null) {
            bg = Ticket.getDefaultBackgroundImage();
        } else {
            bg = ticket.loadBackgroundImage();
        }
        this.getLayer().draw(bg, 0, 0);
    }

    private void renderTicket() {
        this.getLayer(1).clear();

        Ticket ticket = TicketStore.getTicketFromItem(this.getMapItem());
        if (ticket == null) {
            this.getLayer(1).draw(MapFont.MINECRAFT, 10, 40, MapColorPalette.COLOR_RED, "Invalid Ticket");
        } else {
            this.getLayer(1).draw(MapFont.MINECRAFT, 10, 40, MapColorPalette.COLOR_BLACK, ticket.getName());
            if (TicketStore.isTicketExpired(this.getMapItem())) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 57, MapColorPalette.COLOR_RED, "EXPIRED");
            } else {
                String text;
                if (ticket.getMaxNumberOfUses() == 1) {
                    text = "Single use";
                } else if (ticket.getMaxNumberOfUses() < 0) {
                    text = "Unlimited uses";
                } else {
                    text = TicketStore.getNumberOfUses(this.getMapItem()) + "/" + ticket.getMaxNumberOfUses() + " uses";
                }
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 57, MapColorPalette.COLOR_BLACK, text);
            }

            String ownerName = ItemUtil.getMetaTag(this.getMapItem(), false).getValue("ticketOwnerName", "Unknown Owner");
            ownerName = ChatColor.stripColor(ownerName);
            if (TicketStore.isTicketOwner(this.getOwners().get(0), this.getMapItem())) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 74, MapColorPalette.COLOR_BLACK, ownerName);
            } else {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 74, MapColorPalette.COLOR_RED, ownerName);
            }
        }
    }
}
