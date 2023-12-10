package com.bergerkiller.bukkit.tc.attachments.ui.functions;

import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetScroller;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionList;

/**
 * Shows a {@link TransferFunctionList} and interactive controls to make changes to it
 */
public class MapWidgetTransferFunctionList extends MapWidgetScroller {
    private final MapWidgetTransferFunctionDialog dialog;
    private final TransferFunctionList list;
    private int onOpenSelectedIndex = -1;

    public MapWidgetTransferFunctionList(MapWidgetTransferFunctionDialog dialog, TransferFunctionList list) {
        this.dialog = dialog;
        this.list = list;
        this.setBounds(5, 9, dialog.getWidth() - 10, dialog.getHeight() - 20);
        this.setScrollPadding(10);
    }

    public void onSelectedItemChanged() {
    }

    public int getSelectedItemIndex() {
        MapWidget w = display.getFocusedWidget();
        if (w instanceof MapWidgetTransferFunctionListItem) {
            return list.indexOf(((MapWidgetTransferFunctionListItem) w).getItem());
        } else {
            return -1;
        }
    }

    public MapWidgetTransferFunctionList setSelectedItemIndex(int index) {
        onOpenSelectedIndex = index;
        return this;
    }

    @Override
    public void onAttached() {
        int index = 0;
        for (TransferFunctionList.Item listItem : list.getItems()) {
            MapWidgetTransferFunctionListItem item = createItem(listItem);
            calcBounds(item, index++);
            addContainerWidget(item);
        }

        addInitialItemPlaceholder();

        if (onOpenSelectedIndex != -1 && onOpenSelectedIndex < getContainer().getWidgetCount()) {
            getContainer().getWidget(onOpenSelectedIndex).focus();
        }

        super.onAttached();
    }

    private MapWidgetTransferFunctionListItem createItem(TransferFunctionList.Item listItem) {
        final MapWidgetTransferFunctionListItem item = new MapWidgetTransferFunctionListItem(
                dialog.getHost(),
                listItem,
                () -> {
                    // Find the item that precedes this item, and get its output boolean state.
                    int index = list.indexOf(listItem);
                    return index != -1 && list.isBooleanOutput(index - 1, dialog::isBooleanInput);
                }
        ) {
            @Override
            public void onMoveUp() {
                int currIndex = list.indexOf(getItem());
                if (currIndex != -1 && currIndex > 0) {
                    list.remove(currIndex);
                    list.add(currIndex - 1, getItem());
                    recalcBounds();
                    dialog.markChanged();
                }
            }

            @Override
            public void onMoveDown() {
                int currIndex = list.indexOf(getItem());
                if (currIndex != -1 && currIndex < (list.size() - 1)) {
                    list.remove(currIndex);
                    list.add(currIndex + 1, getItem());
                    recalcBounds();
                    dialog.markChanged();
                }
            }

            @Override
            public void onFunctionModeChanged(TransferFunctionList.Item oldItem, TransferFunctionList.Item newItem) {
                int index = list.indexOf(oldItem);
                if (index != -1) {
                    list.set(index, newItem);
                    dialog.markChanged();
                }
            }

            @Override
            public void onFocus() {
                super.onFocus();
                onSelectedItemChanged();
            }
        };

        item.addButton(MapWidgetTransferFunctionItem.ButtonIcon.CONFIGURE, item::configure)
            .addButton(MapWidgetTransferFunctionItem.ButtonIcon.MOVE,  item::startMove)
            .addButton(MapWidgetTransferFunctionItem.ButtonIcon.ADD, () -> addNewItem(list.indexOf(item.getItem())))
            .addButton(MapWidgetTransferFunctionItem.ButtonIcon.REMOVE, () -> {
                int itemIndex = list.indexOf(item.getItem());
                if (itemIndex != -1) {
                    // Remove the item at this index
                    list.remove(itemIndex);
                    MapWidgetTransferFunctionList.this.getContainer().removeWidget(item);
                    addInitialItemPlaceholder();
                    recalcBounds();
                    dialog.markChanged();

                    // Select the item that is now at this index
                    if (itemIndex >= list.size()) {
                        itemIndex = list.size() - 1;
                    }
                    boolean found = false;
                    if (itemIndex >= 0) {
                        TransferFunctionList.Item newSelListItem = list.get(itemIndex);
                        for (MapWidget w : getContainer().getWidgets()) {
                            if (((MapWidgetTransferFunctionListItem) w).getItem() == newSelListItem) {
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

    private void addNewItem(final int index) {
        dialog.createNew(newFunction -> {
            // Insert a new item below this item
            int newItemIndex = index;
            if (newItemIndex == -1) {
                newItemIndex = list.size();
            } else if (newItemIndex < list.size()) {
                newItemIndex++;
            }
            if (list.isEmpty()) {
                getContainer().clearWidgets();
            }
            TransferFunctionList.Item newListItem = new TransferFunctionList.Item(
                    TransferFunctionList.FunctionMode.ASSIGN, newFunction);
            list.add(newItemIndex, newListItem);
            MapWidgetTransferFunctionListItem newItem = addContainerWidget(createItem(newListItem));
            recalcBounds();
            newItem.focus();
            dialog.markChanged();
        });
    }

    private void addInitialItemPlaceholder() {
        if (list.isEmpty()) {
            // Add a default item that initializes a new function when activated
            // Used for empty lists.
            getContainer().clearWidgets();
            addContainerWidget(new MapWidgetButton() {
                @Override
                public void onActivate() {
                    addNewItem(0);
                }
            }.setText("Set Function").setBounds(0, 0, getWidth(), 13)).focus();
        }
    }

    private void recalcBounds() {
        if (!list.isEmpty()) {
            for (MapWidget w : this.getContainer().getWidgets()) {
                MapWidgetTransferFunctionListItem item = (MapWidgetTransferFunctionListItem) w;
                calcBounds(w, list.indexOf(item.getItem()));
            }
        }
        super.recalculateContainerSize();
    }

    private void calcBounds(MapWidget widget, int index) {
        if (index == -1) {
            throw new IllegalArgumentException("Index is -1");
        }
        widget.setBounds(0, (MapWidgetTransferFunctionItem.HEIGHT - 1) * index,
                         getWidth(), MapWidgetTransferFunctionItem.HEIGHT);
    }
}
