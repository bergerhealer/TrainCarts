package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelection;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentSelector;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentSeat;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentSelector;
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
            function.setSeatSelector(AttachmentSelector.readFromConfig(config, "seat")
                    .withType(CartAttachmentSeat.class));
            function.updateSource(host);
            return function;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionInputSeatOccupied function) {
            function.getSeatSelector().writeToConfig(config, "seat");
        }
    };

    private AttachmentSelector<CartAttachmentSeat> seatSelector = AttachmentSelector.all(CartAttachmentSeat.class);

    /**
     * Sets a name filter. If set to non-empty, only seats with this name will
     * be considered.
     *
     * @param name Seat name filter
     */
    public void setNameFilter(String name) {
        this.seatSelector = seatSelector.withName(name);
    }

    public void setSeatSelector( AttachmentSelector<CartAttachmentSeat> selector) {
        this.seatSelector = selector;
    }

    public AttachmentSelector<CartAttachmentSeat> getSeatSelector() {
        return seatSelector;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public ReferencedSource createSource(TransferFunctionHost host) {
        Attachment attachment = host.getAttachment();
        if (attachment != null) {
            return new SeatOccupiedReferencedSource(attachment.getSelection(seatSelector));
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

        dialog.addLabel(29, 21, MapColorPalette.COLOR_RED, "Monitored Seats");
        dialog.addWidget(new SeatNameWidget() {
            @Override
            public void onChanged() {
                dialog.markChanged();
            }

            @Override
            public List<String> getSeatNames(AttachmentSelector<CartAttachmentSeat> allSelector) {
                Attachment attachment = dialog.getHost().getAttachment();
                if (attachment != null) {
                    return attachment.getSelection(allSelector).names();
                } else {
                    return Collections.emptyList();
                }
            }
        }).setBounds(11, 27, 92, 13);
    }

    private abstract class SeatNameWidget extends MapWidget {
        private final byte COLOR_BG_DEFAULT = MapColorPalette.getColor(199, 199, 199);
        private final byte COLOR_BG_FOCUSED = MapColorPalette.getColor(255, 252, 245);

        public SeatNameWidget() {
            this.setFocusable(true);
        }

        public abstract void onChanged();

        public abstract List<String> getSeatNames(AttachmentSelector<CartAttachmentSeat> allSelector);

        @Override
        public void onActivate() {
            getParent().addWidget(new MapWidgetAttachmentSelector<CartAttachmentSeat>(
                    getSeatSelector()
            ) {
                @Override
                public List<String> getAttachmentNames(AttachmentSelector<CartAttachmentSeat> allSelector) {
                    return getSeatNames(allSelector);
                }

                @Override
                public void onSelected(AttachmentSelector<CartAttachmentSeat> selection) {
                    setSeatSelector(selection);
                    onChanged();
                }
            }.setTitle("Set Seat name")
             .includeAny("<Any Seat>"));
        }

        @Override
        public void onDraw() {
            view.drawRectangle(0, 0, getWidth(), getHeight(), MapColorPalette.COLOR_BLACK);
            view.fillRectangle(1, 1, getWidth() - 2, getHeight() - 2,
                    isFocused() ? COLOR_BG_FOCUSED : COLOR_BG_DEFAULT);

            String text;
            byte textColor;
            if (seatSelector.nameFilter().isPresent()) {
                text = seatSelector.nameFilter().get();
                textColor = isFocused() ? MapColorPalette.COLOR_BLUE : MapColorPalette.COLOR_BLACK;
            } else {
                text = "<Any Seat>";
                textColor = MapColorPalette.getColor(128, 128, 128);
            }
            int textWidth = (int) view.calcFontSize(MapFont.MINECRAFT, text).getWidth();
            view.draw(MapFont.MINECRAFT, (getWidth() - textWidth + 1) / 2, 3, textColor, text);
        }
    }

    private static class SeatOccupiedReferencedSource extends ReferencedSource {
        private final AttachmentSelection<CartAttachmentSeat> seatSelection;

        public SeatOccupiedReferencedSource(AttachmentSelection<CartAttachmentSeat> seatSelection) {
            this.seatSelection = seatSelection;
        }

        @Override
        public void onTick() {
            // Refresh seats if changed
            seatSelection.sync();

            // Check whether any seats are occupied, and if so, set to 1.0. Otherwise 0.0
            double result = 0.0;
            for (CartAttachmentSeat seat : seatSelection) {
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
                return this.seatSelection.selector().equals(other.seatSelection.selector());
            } else {
                return false;
            }
        }
    }
}
