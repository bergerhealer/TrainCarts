package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentNameLookup;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNameSelector;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;

import java.util.Collections;
import java.util.List;

public class TransferFunctionInputSeatOccupied extends TransferFunctionInput {
    public static final Serializer<TransferFunctionInputSeatOccupied> SERIALIZER = new Serializer<TransferFunctionInputSeatOccupied>() {
        @Override
        public String typeId() {
            return "INPUT-SEAT-OCCUPIED";
        }

        @Override
        public String title() {
            return "In: Seat Occupied";
        }

        @Override
        public boolean isInput() {
            return true;
        }

        @Override
        public boolean isListed(TransferFunctionHost host) {
            return host.isAttachment();
        }

        @Override
        public TransferFunctionInputSeatOccupied createNew(TransferFunctionHost host) {
            TransferFunctionInputSeatOccupied function = new TransferFunctionInputSeatOccupied();
            function.updateSource(host);
            return function;
        }

        @Override
        public TransferFunctionInputSeatOccupied load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionInputSeatOccupied function = new TransferFunctionInputSeatOccupied();
            function.setNameFilter(config.getOrDefault("seatName", ""));
            function.setRelativeToAttachment(config.getOrDefault("relativeSearch", false));
            function.updateSource(host);
            return function;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInputSeatOccupied function) {
            config.set("seatName", function.nameFilter.isEmpty() ? null : function.nameFilter);
            config.set("relativeSearch", function.relativeToAttachment ? true : null);
        }
    };

    private String nameFilter = "";
    private boolean relativeToAttachment = false;

    /**
     * Sets a name filter. If set to non-empty, only seats with this name will
     * be considered.
     *
     * @param name Seat name filter
     */
    public void setNameFilter(String name) {
        this.nameFilter = name;
    }

    /**
     * Sets whether only seats that are children of 'this' attachment are considered.
     * If false, then all attachments from the root of the attachment tree are found.
     *
     * @param relative True if relative to the attachment this function is assigned to,
     *                 False if it is relative to the root of the attachment tree.
     */
    public void setRelativeToAttachment(boolean relative) {
        this.relativeToAttachment = relative;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        Attachment attachment = host.getAttachment();
        if (attachment != null) {
            return new SeatOccupiedReferencedSource(attachment, relativeToAttachment, nameFilter);
        } else {
            return ReferencedSource.NONE;
        }
    }

    @Override
    protected TransferFunctionInput cloneInput() {
        return new TransferFunctionInputSpeed();
    }

    @Override
    public boolean isBooleanOutput() {
        return true;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 0, 3, MapColorPalette.COLOR_GREEN, "<Seat Occupied>");
    }

    @Override
    public void openDialog(Dialog dialog) {
        super.openDialog(dialog);

        dialog.addLabel(28, 20, MapColorPalette.COLOR_RED, "Search Strategy");
        dialog.addWidget(new MapWidgetButton() {
            @Override
            public void onAttached() {
                updateText();
                super.onAttached();
            }

            @Override
            public void onActivate() {
                relativeToAttachment = !relativeToAttachment;
                dialog.markChanged();
                updateText();
            }

            private void updateText() {
                setText(relativeToAttachment ? "Seat Children" : "All Cart Seats");
            }
        }).setBounds(11, 26, 92, 13);

        dialog.addLabel(29, 42, MapColorPalette.COLOR_RED, "Seat Name Filter");
        dialog.addWidget(new SeatNameWidget() {
            @Override
            public void onChanged() {
                dialog.markChanged();
            }

            @Override
            public List<String> getSeatNames() {
                Attachment attachment = dialog.getHost().getAttachment();
                if (attachment != null) {
                    if (!relativeToAttachment) {
                        attachment = attachment.getRootParent();
                    }
                    return attachment.getNameLookup().names(a -> a instanceof CartAttachmentSeat);
                } else {
                    return Collections.emptyList();
                }
            }
        }).setBounds(11, 48, 92, 13);
    }

    private abstract class SeatNameWidget extends MapWidget {
        private final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
        private final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);

        public SeatNameWidget() {
            this.setFocusable(true);
        }

        public abstract void onChanged();

        public abstract List<String> getSeatNames();

        @Override
        public void onActivate() {
            getParent().addWidget(new MapWidgetAttachmentNameSelector(getSeatNames()) {
                @Override
                public void onSelected(String attachmentName) {
                    nameFilter = attachmentName;
                    onChanged();
                }
            }.setTitle("Set Seat name")
             .includeNone("<Any Seat>"));
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT);

            String text;
            byte textColor;
            if (nameFilter.isEmpty()) {
                text = "<Any Seat>";
                textColor = MapColorPalette.getColor(128, 128, 128);
            } else {
                text = nameFilter;
                textColor = isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK;
            }
            int textWidth = (int) view.calcFontSize(MapFont.MINECRAFT, text).getWidth();
            view.draw(MapFont.MINECRAFT, (getWidth() - textWidth + 1) / 2, 3, textColor, text);
        }
    }

    private static class SeatOccupiedReferencedSource extends ReferencedSource {
        private final Attachment attachment;
        private final boolean relativeSearch;
        private final String nameFilter;
        private AttachmentNameLookup lookup = AttachmentNameLookup.EMPTY; // Fetches on first onTick()
        private List<CartAttachmentSeat> lastSeats = Collections.emptyList();

        public SeatOccupiedReferencedSource(Attachment attachment, boolean relativeSearch, String nameFilter) {
            this.attachment = attachment;
            this.relativeSearch = relativeSearch;
            this.nameFilter = nameFilter;
        }

        @Override
        public void onTick() {
            // Refresh seats if changed
            if (!lookup.isValid()) {
                Attachment searchRoot = relativeSearch ? attachment : attachment.getRootParent();
                lookup = searchRoot.getNameLookup();
                if (nameFilter.isEmpty()) {
                    lastSeats = lookup.allOfType(CartAttachmentSeat.class);
                } else {
                    lastSeats = lookup.getOfType(nameFilter, CartAttachmentSeat.class);
                }
            }

            // Check whether any seats are occupied, and if so, set to 1.0. Otherwise 0.0
            double result = 0.0;
            for (CartAttachmentSeat seat : lastSeats) {
                if (seat.getEntity() != null) {
                    result = 1.0;
                    break;
                }
            }
            value = result;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof SeatOccupiedReferencedSource) {
                SeatOccupiedReferencedSource other = (SeatOccupiedReferencedSource) o;
                return this.relativeSearch == other.relativeSearch &&
                        this.nameFilter.equals(other.nameFilter);
            } else {
                return false;
            }
        }
    }
}
