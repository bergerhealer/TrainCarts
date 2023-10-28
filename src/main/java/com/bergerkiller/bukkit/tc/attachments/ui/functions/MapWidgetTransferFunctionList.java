package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionList;

public class MapWidgetTransferFunctionList extends MapWidgetScroller {
    private static final int ROW_HEIGHT = 14;
    private final MapWidgetTransferFunctionDialog dialog;
    private final TransferFunctionList list;

    public MapWidgetTransferFunctionList(MapWidgetTransferFunctionDialog dialog, TransferFunctionList list) {
        this.dialog = dialog;
        this.list = list;
        this.setBounds(5, 9, dialog.getWidth() - 10, dialog.getHeight() - 20);
        this.setScrollPadding(10);
    }

    @Override
    public void onAttached() {
        int index = 0;
        for (TransferFunction function : list.getFunctions()) {
            MapWidgetTransferFunctionItem item = createItem(function);
            calcBounds(item, index++);
            addContainerWidget(item);
        }

        addInitialItemPlaceholder();

        super.onAttached();
    }

    private MapWidgetTransferFunctionItem createItem(TransferFunction function) {
        MapWidgetTransferFunctionItem item = new MapWidgetTransferFunctionItem(dialog, function) {
            @Override
            public void onMoveUp() {
                int currIndex = list.indexOf(getFunction());
                if (currIndex != -1 && currIndex > 0) {
                    list.remove(currIndex);
                    list.add(currIndex - 1, getFunction());
                    recalcBounds();
                    dialog.markChanged();
                }
            }

            @Override
            public void onMoveDown() {
                int currIndex = list.indexOf(getFunction());
                if (currIndex != -1 && currIndex < (list.size() - 1)) {
                    list.remove(currIndex);
                    list.add(currIndex + 1, getFunction());
                    recalcBounds();
                    dialog.markChanged();
                }
            }
        };
        item.addButton(MapWidgetTransferFunctionItem.ButtonIcon.CONFIGURE, MapWidgetTransferFunctionItem::configure)
            .addButton(MapWidgetTransferFunctionItem.ButtonIcon.MOVE, MapWidgetTransferFunctionItem::startMove)
            .addButton(MapWidgetTransferFunctionItem.ButtonIcon.ADD, i -> {
                dialog.createNew(newFunction -> {
                    // Insert a new item below this item
                    int newItemIndex = list.indexOf(i.getFunction());
                    if (newItemIndex == -1) {
                        newItemIndex = list.size();
                    } else {
                        newItemIndex++;
                    }
                    list.add(newItemIndex, newFunction);
                    MapWidgetTransferFunctionItem newItem = addContainerWidget(createItem(newFunction));
                    recalcBounds();
                    newItem.focus();
                    dialog.markChanged();
                });
            })
            .addButton(MapWidgetTransferFunctionItem.ButtonIcon.REMOVE, i -> {
                int itemIndex = list.indexOf(i.getFunction());
                if (itemIndex != -1) {
                    // Remove the item at this index
                    list.remove(itemIndex);
                    MapWidgetTransferFunctionList.this.getContainer().removeWidget(i);
                    addInitialItemPlaceholder();
                    recalcBounds();
                    dialog.markChanged();

                    // Select the item that is now at this index
                    if (itemIndex >= list.size()) {
                        itemIndex = list.size() - 1;
                    }
                    boolean found = false;
                    if (itemIndex >= 0) {
                        TransferFunction newSelFunction = list.get(itemIndex);
                        for (MapWidget w : getContainer().getWidgets()) {
                            if (((MapWidgetTransferFunctionItem) w).getFunction() == newSelFunction) {
                                w.focus();
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found && getContainer().getWidgetCount() > 0) {
                        getContainer().getWidget(0).focus();
                    }
                }
            });
        return item;
    }

    private void addInitialItemPlaceholder() {
        if (list.isEmpty()) {
            // Add a default item that initializes a new function when activated
            // Used for empty lists.
            // TODO: Implement
        }
    }

    private void recalcBounds() {
        for (MapWidget w : this.getContainer().getWidgets()) {
            MapWidgetTransferFunctionItem item = (MapWidgetTransferFunctionItem) w;
            calcBounds(w, list.indexOf(item.getFunction()));
        }
        super.recalculateContainerSize();
    }

    private void calcBounds(MapWidget widget, int index) {
        if (index == -1) {
            throw new IllegalArgumentException("Index is -1");
        }
        widget.setBounds(0, ROW_HEIGHT * index, getWidth(), ROW_HEIGHT + 1);
    }
}
