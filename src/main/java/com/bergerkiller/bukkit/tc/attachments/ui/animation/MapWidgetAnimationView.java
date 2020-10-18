package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;

/**
 * Displays all animation nodes, a header and a scrollbar
 * in one view
 */
public class MapWidgetAnimationView extends MapWidget {
    private static final int SCROLL_WIDTH = 3;
    private MapWidgetAnimationHeader _header;
    private MapWidgetAnimationNode[] _nodes;
    private int _scrollOffset = 0;
    private int _selectedNodeIndex = 0;
    private int _selectedNodeRange = 0;
    private boolean _multiSelectActive = false;
    private boolean _reorderActive = false;
    private Animation _animation = null;
    private MapTexture _scrollbarBG = MapTexture.createEmpty();

    /**
     * Called when the player activates (presses spacebar) with a node selected
     */
    public void onSelectionActivated() {
    }

    /**
     * Called when the selected node is changed
     */
    public void onSelectionChanged() {
    }

    /**
     * Called when the group of selected items needs to be moved
     * up or down. A guarantee is made that this function is only
     * called when moving the items up/down this amount is actually
     * possible.
     * 
     * @param offset Offset to move the items (-1 or 1)
     */
    public void onReorder(int offset) {
    }

    /**
     * Sets the animation displayed inside this view, refreshing
     * all visible nodes.
     * 
     * @param animation
     * @return this animation view widget
     */
    public MapWidgetAnimationView setAnimation(Animation animation) {
        this._animation = animation;
        this.setFocusable(animation != null && animation.getNodeCount() > 0);
        this.updateView();
        return this;
    }

    /**
     * Gets the animation currently displayed inside this view
     * 
     * @return animation
     */
    public Animation getAnimation() {
        return this._animation;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        int pos_y = 1;

        this._header = new MapWidgetAnimationHeader();
        this._header.setBounds(1, pos_y, this.getWidth()-SCROLL_WIDTH-3, 5);
        this.addWidget(this._header);
        pos_y += 6;

        int num_rows = ((this.getHeight()-1) / 6) - 1;
        this._nodes = new MapWidgetAnimationNode[num_rows];
        for (int i = 0; i < num_rows; i++) {
            this._nodes[i] = new MapWidgetAnimationNode();
            this._nodes[i].setBounds(1, pos_y, this.getWidth()-SCROLL_WIDTH-3, 5);
            this.addWidget(this._nodes[i]);
            pos_y += 6;
        }

        this.updateView();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        this.clearWidgets();
        this._header = null;
        this._nodes = null;
    }

    @Override
    public void onDraw() {
        int scroll_height = getHeight()-8;
        int scroll_x = getWidth()-SCROLL_WIDTH-1;
        int scroll_y = getHeight()-scroll_height-1;

        // Draw grid
        byte frameColor = this.isFocused() ? MapColorPalette.COLOR_YELLOW : MapColorPalette.COLOR_BLACK;
        this.view.drawRectangle(0, 0, getWidth()-SCROLL_WIDTH-1, getHeight(), frameColor);
        for (int y = 6; y < (this.getHeight()-1); y += 6) {
            this.view.drawLine(1, y, getWidth()-SCROLL_WIDTH-3, y, MapColorPalette.COLOR_BLACK);
        }

        // Draw scrollbar to the right
        this.view.drawRectangle(scroll_x-1, scroll_y-1,
                SCROLL_WIDTH+2, scroll_height+2, frameColor);

        // Color palette used in the scrollbar
        byte scrollbar_color_lft = MapColorPalette.getColor(115, 164, 174);
        byte scrollbar_color_mid = MapColorPalette.getColor(140, 201, 213);
        byte scrollbar_color_rgt = MapColorPalette.getColor(163, 233, 247);

        // Only draw the scrollbar background when actually needed
        int scrollbar_pos = 0;
        int scrollbar_height = scroll_height;
        if (this._animation != null && this._animation.getNodeCount() > this._nodes.length) {
            // Draw a fancy background for the scrollbar
            if (this._scrollbarBG.getWidth() != SCROLL_WIDTH || this._scrollbarBG.getHeight() != scroll_height) {
                this._scrollbarBG = generateNoise(SCROLL_WIDTH, scroll_height,
                        MapColorPalette.getColor(25, 93, 131),
                        MapColorPalette.getColor(19, 70, 98),
                        MapColorPalette.getColor(31, 114, 160)); 
            }
            this.view.draw(this._scrollbarBG, scroll_x, scroll_y);

            // Compute scrollbar size and position based on how many out-of-view elements exist
            scrollbar_height = Math.max(2, (this._nodes.length * scroll_height) / this._animation.getNodeCount());
            int num_possible_steps = (this._animation.getNodeCount() - this._nodes.length);
            int max_position = scroll_height-scrollbar_height;
            if (this._scrollOffset < 0) {
                scrollbar_pos = 0;
            } else if (this._scrollOffset > num_possible_steps) {
                scrollbar_pos = max_position;
            } else {
                scrollbar_pos = (this._scrollOffset * max_position) / num_possible_steps;
            }
        }

        // Draw the scrollbar handle
        {
            int bar_y1 = scroll_y + scrollbar_pos;
            int bar_y2 = bar_y1 + scrollbar_height - 1;
            this.view.drawLine(scroll_x, bar_y1, scroll_x, bar_y2, scrollbar_color_lft);
            this.view.fillRectangle(scroll_x+1, bar_y1, SCROLL_WIDTH-2, scrollbar_height, scrollbar_color_mid);
            this.view.drawLine(scroll_x+SCROLL_WIDTH-1, bar_y1, scroll_x+SCROLL_WIDTH-1, bar_y2, scrollbar_color_rgt);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();

        updateSelectedNodes();
        this.onSelectionChanged();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();

        if (this._multiSelectActive) {
            this.stopMultiSelect();
        }
        if (this._reorderActive) {
            this.stopReordering();
        }
        for (MapWidgetAnimationNode node : this._nodes) {
            node.setSelected(false);
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        boolean activated = this.isActivated();
        super.onKeyPressed(event);
        if (!activated) {
            return;
        }

        if (this._reorderActive) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                if (this.getSelectionStart() >= 1) {
                    onReorder(-1);
                }
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                if (this.getSelectionEnd() < (this._animation.getNodeCount()-1)) {
                    onReorder(1);
                }
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                this.stopReordering();
            }
        } else if (this._multiSelectActive) {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                this.setSelectedItemRange(this.getSelectedItemRange()-1);
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                this.setSelectedItemRange(this.getSelectedItemRange()+1);
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                this.stopMultiSelect();
            }
        } else {
            if (event.getKey() == MapPlayerInput.Key.UP) {
                this.setSelectedItemRange(0);
                this.setSelectedIndex(this.getSelectedIndex()-1);
            } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
                this.setSelectedItemRange(0);
                this.setSelectedIndex(this.getSelectedIndex()+1);
            } else if (event.getKey() == MapPlayerInput.Key.ENTER) {
                this.onSelectionActivated();
            }
        }
    }

    /**
     * Gets the selected node index
     * 
     * @return selected index
     */
    public int getSelectedIndex() {
        return this._selectedNodeIndex;
    }

    /**
     * Gets the Animation Node currently selected
     * 
     * @return selected node
     */
    public AnimationNode getSelectedNode() {
        if (this._animation == null) return null;
        if (this._selectedNodeIndex >= this._animation.getNodeCount()) return null;
        if (this._selectedNodeIndex < 0) return null;
        return this._animation.getNode(this._selectedNodeIndex);
    }

    /**
     * Gets a list of all the Animation Node entries currently selected
     * 
     * @return list of selected nodes
     */
    public List<AnimationNode> getSelectedNodes() {
        if (this._animation == null) return Collections.emptyList();
        if (this._selectedNodeIndex >= this._animation.getNodeCount()) return Collections.emptyList();
        if (this._selectedNodeIndex < 0) return Collections.emptyList();

        if (this._selectedNodeRange == 0) {
            return Collections.singletonList(this._animation.getNode(this._selectedNodeIndex));
        }

        List<AnimationNode> result = new ArrayList<AnimationNode>(Math.abs(this._selectedNodeRange) + 1);
        int startIndex = this.getSelectionStart();
        int endIndex = this.getSelectionEnd();
        for (int i = startIndex; i <= endIndex; i++) {
            if (i >= 0 && i < this._animation.getNodeCount()) {
                result.add(this._animation.getNode(i));
            }
        }
        return result;
    }

    /**
     * Sets the selected node index
     * 
     * @param index of the node to select
     * @return this animation view widget
     */
    public MapWidgetAnimationView setSelectedIndex(int index) {
        // Impose limits
        if (index < 0 || this._animation == null) {
            index = 0;
        } else if (index >= this._animation.getNodeCount()) {
            index = this._animation.getNodeCount()-1;
        }

        if (index != this._selectedNodeIndex) {
            this._selectedNodeIndex = index;
            this.updateView();
            this.onSelectionChanged();
            display.playSound(SoundEffect.CLICK_WOOD);
        }
        return this;
    }

    /**
     * Gets the number of items additionally selected from the currently
     * selected node. If 0, only the selected node is selected. If positive,
     * additional rows below are selected. If negative, additional rows above
     * are selected.
     * 
     * @return additionally selected item range offset
     */
    public int getSelectedItemRange() {
        return this._selectedNodeRange;
    }

    /**
     * Sets the number of items additionally selected from the currently
     * selected node. See {@link #getSelectedItemRange()}.
     * 
     * @param count Number of items
     * @return this
     */
    public MapWidgetAnimationView setSelectedItemRange(int count) {
        // Impose limits
        if (count < 0) {
            int numBefore = this._selectedNodeIndex;
            if ((-count) > numBefore) {
                count = -numBefore;
            }
        } else if (count > 0) {
            int numAfter = this._animation.getNodeCount() - this._selectedNodeIndex - 1;
            if (count > numAfter) {
                count = numAfter;
            }
        }

        if (count != this._selectedNodeRange) {
            this._selectedNodeRange = count;
            this.updateView();
            this.onSelectionChanged();
            display.playSound(SoundEffect.CLICK_WOOD);
        }

        return this;
    }

    /**
     * Gets the index of the first animation node that is selected
     * 
     * @return selection start
     */
    public int getSelectionStart() {
        return this._selectedNodeRange < 0 ?
                (this._selectedNodeIndex + this._selectedNodeRange) : this._selectedNodeIndex;
    }

    /**
     * Gets the index of the last animation node that is selected
     * 
     * @return selection end
     */
    public int getSelectionEnd() {
        return this._selectedNodeRange > 0 ?
                (this._selectedNodeIndex + this._selectedNodeRange) : this._selectedNodeIndex;
    }

    /**
     * Activates the multi-select node. The up-down keys now select or deselect additional
     * entries above/below the currently selected node. Spacebar completes the selection.
     * From then on, behavior resumes as normal.
     */
    public void startMultiSelect() {
        this._multiSelectActive = true;
        display.playSound(SoundEffect.PISTON_EXTEND);
        this.updateView();
    }

    /**
     * Stops multi-select mode. See {@link #startMultiSelect()}
     */
    public void stopMultiSelect() {
        this._multiSelectActive = false;
        display.playSound(SoundEffect.PISTON_CONTRACT);
        this.updateView();
    }

    /**
     * Starts re-ordering mode. When up/down keys are pressed,
     * the re-order callback is called. When enter is pressed,
     * this mode is automatically exited.
     */
    public void startReordering() {
        _reorderActive = true;
        display.playSound(SoundEffect.PISTON_EXTEND);
        updateView();
    }

    /**
     * Exits re-ordering mode.
     */
    public void stopReordering() {
        _reorderActive = false;
        display.playSound(SoundEffect.PISTON_CONTRACT);
        updateView();
    }

    private void updateView() {
        // Ensure selected node index is within range
        boolean selectionWasWrong = false;
        if (this._animation != null && this._selectedNodeIndex >= this._animation.getNodeCount()) {
            this._selectedNodeIndex = this._animation.getNodeCount()-1;
            selectionWasWrong = true;
        }
        if (this._selectedNodeRange < 0) {
            int numBefore = this._selectedNodeIndex;
            if ((-this._selectedNodeRange) > numBefore) {
                this._selectedNodeRange = -numBefore;
                selectionWasWrong = true;
            }
        } else if (this._selectedNodeRange > 0) {
            int numAfter = (this._animation.getNodeCount() - this._selectedNodeIndex - 1);
            if (this._selectedNodeRange > numAfter) {
                this._selectedNodeRange = numAfter;
                selectionWasWrong = true;
            }
        }
        if (selectionWasWrong) {
            this.onSelectionChanged();
        }

        // Ensure selection is visible at all times, scroll to it if needed
        if (this._nodes != null) {
            int focusedIndex = this._selectedNodeIndex + this._selectedNodeRange;
            int relIndex = focusedIndex - this._scrollOffset;
            if (relIndex < 0) {
                this._scrollOffset = focusedIndex;
            } else if (relIndex >= this._nodes.length) {
                this._scrollOffset = focusedIndex - this._nodes.length + 1;
            }
        }

        // Correct scrollbar offset that is out of bounds
        if (this._animation != null) {
            if (this._scrollOffset < 0) {
                this._scrollOffset = 0;
            } else if (this._scrollOffset >= this._animation.getNodeCount()) {
                this._scrollOffset = this._animation.getNodeCount()-1;
            }
        }

        // Invalidate self - we must redraw the scrollbar sometimes
        // TODO: Optimize it so this is not needed all the time?
        this.invalidate();

        // Update the values displayed inside the nodes
        if (this._nodes != null) {
            if (this._animation == null) {
                // No animation, empty view
                for (MapWidgetAnimationNode node : this._nodes) {
                    node.setValue(null);
                    node.setIsMultiSelectRoot(false);
                    node.setSelected(false);
                }
            } else {
                // Compute the maximum position offset (x/y/z) used in the animation
                // This is used to normalize the bar display for position
                double max_position = 0.0;
                for (AnimationNode node : this._animation.getNodeArray()) {
                    Vector pos = node.getPosition();
                    max_position = Math.max(max_position, Math.abs(pos.getX()));
                    max_position = Math.max(max_position, Math.abs(pos.getY()));
                    max_position = Math.max(max_position, Math.abs(pos.getZ()));
                }

                // Update node displays
                int anim_idx = this._scrollOffset;
                for (MapWidgetAnimationNode node : this._nodes) {
                    node.setMaximumPosition(max_position);
                    node.setValue((anim_idx >= this._animation.getNodeCount()) ? null : this._animation.getNode(anim_idx));
                    anim_idx++;
                }

                // Update selected, do not show selection when deactivated
                updateSelectedNodes();
            }
        }
    }

    private void updateSelectedNodes() {
        if (this.isActivated()) {
            int relIndex = this._selectedNodeIndex - this._scrollOffset;
            int relIndexStart = this.getSelectionStart() - this._scrollOffset;
            int relIndexEnd = this.getSelectionEnd() - this._scrollOffset;

            for (int i = 0; i < this._nodes.length; i++) {
                this._nodes[i].setIsMultiSelectRoot(this._multiSelectActive && i == relIndex);
                this._nodes[i].setSelected(i >= relIndexStart && i <= relIndexEnd);
            }
        } else {
            for (int i = 0; i < this._nodes.length; i++) {
                this._nodes[i].setIsMultiSelectRoot(false);
                this._nodes[i].setSelected(false);
            }
        }
    }

    /**
     * Generates a noise texture of a selection of colors
     * 
     * @param width
     * @param height
     * @param colors
     * @return texture
     */
    private static MapTexture generateNoise(int width, int height, byte... colors) {
        Random rand = new Random(0x3535332L);
        MapTexture result = MapTexture.createEmpty(width, height);
        byte[] buffer = result.getBuffer();
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = colors[rand.nextInt(colors.length)];
        }
        return result;
    }
}
