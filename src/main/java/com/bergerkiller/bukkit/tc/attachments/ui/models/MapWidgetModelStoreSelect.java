package com.bergerkiller.bukkit.tc.attachments.ui.models;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shows the model name that is selected by the user, together with a fold-down
 * button that allows popping open a selection list of models. Activating the
 * widget itself opens a selection dialog by name.
 */
public abstract class MapWidgetModelStoreSelect extends MapWidget {
    private final TrainCarts traincarts;
    private SavedAttachmentModel selected = null;
    private final MapWidgetSubmitText submitText;
    private final MapWidgetModelName modelName;
    private final MapWidgetDropDownListButton dropDownListButton;
    private int listNumRows = 7;

    public MapWidgetModelStoreSelect(TrainCarts traincarts) {
        this.traincarts = traincarts;
        setRetainChildWidgets(true);

        this.submitText = this.addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAccept(String text) {
                display.playSound(SoundEffect.CLICK_WOOD);
                setSelectedModelCheckChanged(text);
            }

            @Override
            public void onCancel() {
            }
        });
        this.submitText.setDescription("Enter Model Name");

        this.modelName = this.addWidget(new MapWidgetModelName() {
            @Override
            public void onActivate() {
                display.playSound(SoundEffect.CLICK);
                submitText.activate();
            }
        });

        this.dropDownListButton = this.addWidget(new MapWidgetDropDownListButton() {
            @Override
            public void onActivate() {
                openDropDownModelList();
            }
        });
    }

    public abstract void onSelectedModelChanged(SavedAttachmentModel model);

    public MapWidgetModelStoreSelect setSelectedModelName(String modelName) {
        return setSelectedModel(traincarts.getSavedAttachmentModels().getModelOrNone(modelName));
    }

    public MapWidgetModelStoreSelect setSelectedModel(SavedAttachmentModel model) {
        this.selected = model;
        this.modelName.invalidate();
        return this;
    }

    public MapWidgetModelStoreSelect setListNumRows(int num) {
        this.listNumRows = num;
        return this;
    }

    public SavedAttachmentModel getSelectedModel() {
        return selected;
    }

    public void openDropDownModelList() {
        // How many rows can we display below this widget and the max height of the 128x128 map?
        int listYPos = (getAbsoluteY() + getHeight() - 1);
        int listHeight = listNumRows * MapWidgetDropDownList.ROW_HEIGHT + MapWidgetDropDownList.PADDING;

        // Add a new MapWidgetDropDownList below this button and activate it to capture all input
        // Sneak/activating an item closes the dialog again
        addWidget(new MapWidgetDropDownList(listNumRows) {
            @Override
            public void onModelSelected(SavedAttachmentModel selectedModel) {
                setSelectedModelCheckChanged(getSelectedModel());
            }

            @Override
            public void onClosed() {
                display.playSound(SoundEffect.PISTON_CONTRACT);
                removeWidget();
                dropDownListButton.focus();
            }
        }.setBounds(getAbsoluteX(), listYPos, getWidth(), listHeight));

        display.playSound(SoundEffect.PISTON_EXTEND);
    }

    @Override
    public void onBoundsChanged() {
        modelName.setBounds(0, 0, getWidth() - getHeight(), getHeight());
        dropDownListButton.setBounds(getWidth() - getHeight(), 0, getHeight(), getHeight());
    }

    private boolean setSelectedModelCheckChanged(String name) {
        if (name == null || name.trim().isEmpty()) {
            return setSelectedModelCheckChanged((SavedAttachmentModel) null);
        } else {
            return setSelectedModelCheckChanged(traincarts.getSavedAttachmentModels().getModelOrNone(name));
        }
    }

    private boolean setSelectedModelCheckChanged(SavedAttachmentModel newModel) {
        if (LogicUtil.bothNullOrEqual(selected, newModel)) {
            return false;
        }

        this.selected = newModel;
        this.modelName.invalidate();
        this.onSelectedModelChanged(newModel);
        return true;
    }

    private class MapWidgetModelName extends MapWidgetBordered {

        public MapWidgetModelName() {
            setFocusable(true);
        }

        @Override
        public void onDraw() {
            drawBackground(true, isFocused()); // Border
            if (selected != null) {
                view.draw(MapFont.MINECRAFT, 3, 3,
                        selected.isNone() ? MapColorPalette.COLOR_RED
                                : (isFocused() ? MapColorPalette.COLOR_YELLOW
                                               : MapColorPalette.COLOR_BLACK),
                        selected.getName());
            } else {
                view.draw(MapFont.MINECRAFT, 3, 3,
                        MapColorPalette.getColor(80, 64, 64),
                        "-- not set --");
            }
        }
    }

    private class MapWidgetDropDownListButton extends MapWidgetBordered {

        public MapWidgetDropDownListButton() {
            setFocusable(true);
        }

        @Override
        public void onDraw() {
            drawBackground(false, isFocused()); // Border

            // Triangle icon
            int t_w = getWidth() - 7;
            drawUpsideDownTriangleFrom(3, getHeight() - 3 - (t_w/2), t_w,
                    isFocused() ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_BLACK);
        }

        private void drawUpsideDownTriangleFrom(int tl_x, int tl_y, int w, byte color) {
            while (w > 0) {
                view.drawLine(tl_x, tl_y, tl_x + w, tl_y, color);

                w -= 2;
                tl_x += 1;
                tl_y += 1;
            }
        }
    }

    private abstract class MapWidgetDropDownList extends MapWidgetBordered {
        public static final int ROW_HEIGHT = 8;
        public static final int PADDING = 4;
        private final int numberOfRows;
        private List<SavedAttachmentModel> models;
        private int selectedIndex;
        private int scrollOffset;

        public MapWidgetDropDownList(int numberOfRows) {
            this.setPositionAbsolute(true);
            this.numberOfRows = numberOfRows;
            setFocusable(true);
            setDepthOffset(2);
        }

        public abstract void onModelSelected(SavedAttachmentModel selectedModel);

        public abstract void onClosed();

        public SavedAttachmentModel getSelectedModel() {
            return models.isEmpty() ? null : models.get(selectedIndex);
        }

        @Override
        public void onAttached() {
            models = traincarts.getSavedAttachmentModels().getAll();
            if (selected == null) {
                selectedIndex = 0;
            } else if ((selectedIndex = models.indexOf(selected)) == -1) {
                // Add an extra entry for the selected item, and select that one
                models = new ArrayList<>(models);
                models.add(selected);
                models.sort(Comparator.comparing(SavedAttachmentModel::getName));
                selectedIndex = models.indexOf(selected);
            }
            scrollToSelection();

            this.activate();
        }

        @Override
        public void onDraw() {
            drawBackground(false, false);

            byte selectedBGColor = MapColorPalette.getColor(140, 140, 140);
            byte selectedColor = MapColorPalette.COLOR_YELLOW;
            byte unselectedColor = MapColorPalette.getColor(190, 190, 190);
            for (int i = 0; i < numberOfRows; i++) {
                int index = (scrollOffset + i);
                if (index < models.size()) {
                    byte color = unselectedColor;
                    if (index == selectedIndex) {
                        color = selectedColor;
                        view.fillRectangle(2, 2 + i * ROW_HEIGHT, getWidth()-4, ROW_HEIGHT, selectedBGColor);
                    }
                    view.draw(MapFont.MINECRAFT, 2, 2 + i * ROW_HEIGHT, color, models.get(index).getName());
                }
            }
        }

        @Override
        public void onKeyPressed(MapKeyEvent event) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                if (selectedIndex > 0) {
                    selectedIndex = Math.max(0, selectedIndex - 1);
                    scrollToSelection();
                    invalidate();
                }
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                int maxIndex = (models.size() - 1);
                if (selectedIndex < maxIndex) {
                    selectedIndex = Math.min(maxIndex, selectedIndex + 1);
                    scrollToSelection();
                    invalidate();
                }
            } else if (event.getKey() == MapPlayerInput.Key.BACK) {
                onClosed();
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                onModelSelected(selected);
                onClosed();
            }
        }

        private void scrollToSelection() {
            if (models.size() < numberOfRows) {
                scrollOffset = 0;
            } else if (selectedIndex < scrollOffset) {
                scrollOffset = selectedIndex;
            } else if (selectedIndex >= (scrollOffset + numberOfRows)) {
                scrollOffset = selectedIndex - numberOfRows + 1;
            }
        }
    }

    // Draws a shaded border around the widget's bounds
    private static class MapWidgetBordered extends MapWidget {

        public void drawBackground(boolean invertedBorderColors, boolean focused) {
            byte color_edge = focused ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_BLACK;
            byte color_inner_tl = MapColorPalette.getColor(143, 143, 143);
            byte color_inner_br = MapColorPalette.getColor(66, 66, 66);
            byte color_inner = MapColorPalette.getColor(112, 112, 112);

            if (invertedBorderColors) {
                byte b = color_inner_tl;
                color_inner_tl = color_inner_br;
                color_inner_br = b;
            }

            // Outer black edge
            view.drawLine(1, 0, getWidth() - 2, 0, color_edge);
            view.drawLine(1, getHeight()-1, getWidth() - 2, getHeight()-1, color_edge);
            view.drawLine(0, 1, 0, getHeight()-2, color_edge);
            view.drawLine(getWidth()-1, 1, getWidth()-1, getHeight()-2, color_edge);

            // Inner shaded effect
            view.drawLine(1, 1, 1, getHeight()-2, color_inner_tl);
            view.drawLine(1, 1, getWidth()-2, 1, color_inner_tl);
            view.drawLine(getWidth()-2, 1, 1, getHeight()-2, color_inner_tl);
            view.drawLine(getWidth()-2, 2, getWidth()-2, getHeight()-2, color_inner_br);
            view.drawLine(2, getHeight()-2, getWidth()-2, getHeight()-2, color_inner_br);

            // Inner text background
            view.fillRectangle(2, 2, getWidth()-4, getHeight()-4, color_inner);
        }
    }
}
