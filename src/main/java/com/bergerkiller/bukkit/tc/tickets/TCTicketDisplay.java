package com.bergerkiller.bukkit.tc.tickets;

import org.bukkit.ChatColor;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.utils.ItemUtil;
import com.bergerkiller.bukkit.tc.Localization;

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
            this.getLayer(1).draw(MapFont.MINECRAFT, 10, 40, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_INVALID.get());
        } else {
            this.getLayer(1).draw(MapFont.MINECRAFT, 10, 40, MapColorPalette.COLOR_BLACK, ticket.getName());
            if (TicketStore.isTicketExpired(this.getMapItem())) {
                this.getLayer(1).draw(MapFont.MINECRAFT, 10, 57, MapColorPalette.COLOR_RED, Localization.TICKET_MAP_EXPIRED.get());
            } else {
                int maxUses = ticket.getMaxNumberOfUses();
                int numUses = (maxUses == 1) ? 0 : TicketStore.getNumberOfUses(this.getMapItem());
                if (maxUses < 0) {
                    maxUses = -1; // Just in case, so it works properly with Localization
                }
                String text = Localization.TICKET_MAP_USES.get(Integer.toString(maxUses), Integer.toString(numUses));
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
