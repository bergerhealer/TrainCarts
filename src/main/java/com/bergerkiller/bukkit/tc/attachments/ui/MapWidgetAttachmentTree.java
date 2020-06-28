package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.List;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;

/**
 * The tree view storing all the map widget attachment nodes.
 * Manages the selection and adding/removing of nodes in the tree.
 * Also manages the view area scrolling for larger trees.
 */
public abstract class MapWidgetAttachmentTree extends MapWidget {
    private static final int MAX_VISIBLE_DEPTH = 3;
    private MapWidgetAttachmentNode root = new MapWidgetAttachmentNode(this);
    private int offset = 0;
    private int count = 6;
    private int lastSelIdx = 0;
    private int column_offset = 0;
    private boolean resetNeeded;
    private AttachmentModel model = null;

    public AttachmentModel getModel() {
        return this.model;
    }

    public void setModel(AttachmentModel model) {
        this.model = model;
        this.root = new MapWidgetAttachmentNode(this);
        this.root.loadConfig(model.getConfig());
        this.lastSelIdx = this.root.getEditorOption("selectedIndex", 0);
        this.updateView(this.root.getEditorOption("scrollOffset", 0));
    }

    public void updateModel(boolean notify) {
        this.model.update(root.getFullConfig(), notify);
        this.getEditor().onSelectedNodeChanged();
    }

    public void updateModelNode(MapWidgetAttachmentNode node, boolean notify) {
        this.model.updateNode(node.getTargetPath(), node.getConfig(), notify);
        this.getEditor().onSelectedNodeChanged();
    }

    public abstract void onMenuOpen(MapWidgetAttachmentNode node, MapWidgetAttachmentNode.MenuItem menu);

    public MapWidgetAttachmentNode getRoot() {
        return this.root;
    }

    public MapWidgetAttachmentNode getSelectedNode() {
        if (this.lastSelIdx >= 0 && this.lastSelIdx < this.getWidgetCount()) {
            return (MapWidgetAttachmentNode) this.getWidget(this.lastSelIdx);
        }
        return this.root;
    }

    @Override
    public void onAttached() {
        /*
        MapWidgetAttachmentNode a = root.addAttachment();
        {
            a.addAttachment();

            MapWidgetAttachmentNode b = a.addAttachment();
            {
                b.addAttachment();
            }

            a.addAttachment();
            a.addAttachment();
        }
        {
            a.addAttachment();
        }
        */
        
        //root.addAttachment();

        this.updateView();
    }

    @Override
    public void onTick() {
        if (this.resetNeeded) {
            this.updateView(this.offset);
        }
    }

    @Override
    public void onDraw() {
        //view.fill(MapColorPalette.COLOR_GREEN);
    }

    /**
     * Gets a list of visible node widgets
     * 
     * @return list
     */
    public List<MapWidgetAttachmentNode> getVisibleNodes() {
        return CommonUtil.unsafeCast(this.getWidgets());
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        List<MapWidgetAttachmentNode> widgets = this.getVisibleNodes();
        if (widgets.isEmpty()) {
            return;
        }

        this.lastSelIdx = widgets.indexOf(this.getNextInputWidget());
        MapWidgetAttachmentNode currentlySelected = this.getSelectedNode();
        if (currentlySelected != null && currentlySelected.isChangingOrder()) {
            MapWidgetAttachmentNode selected = widgets.get(this.lastSelIdx);

            // Complete changing order of widget
            if (event.getKey() == MapPlayerInput.Key.ENTER || event.getKey() == MapPlayerInput.Key.BACK) {
                selected.setChangingOrder(false);
                display.playSound(SoundEffect.CLICK);
                return;
            }

            MapPlayerInput.Key action = event.getKey();

            // Move up or down in the order of attachments of the current parent node
            // [/]            [/]
            //    [*]  <===>     [/]
            //    [/]            [*]
            if (action == MapPlayerInput.Key.UP || action == MapPlayerInput.Key.DOWN) {
                MapWidgetAttachmentNode parent = selected.getParentAttachment();
                if (parent != null) {
                    List<MapWidgetAttachmentNode> attachments = parent.getAttachments();
                    int old_index = attachments.indexOf(selected);
                    int new_index = old_index + ((action == MapPlayerInput.Key.UP) ? -1 : 1);
                    if (new_index >= 0 && new_index < attachments.size()) {
                        attachments.remove(old_index);
                        attachments.add(new_index, selected);

                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                        this.resetNeeded = true;
                    }
                }
            }

            // Make the node a child on the same level as its current parent
            // [/]            [/]
            //    [*]   ===>  [*]
            // [/]            [/]
            if (action == MapPlayerInput.Key.LEFT) {
                MapWidgetAttachmentNode parent = selected.getParentAttachment();
                if (parent != null && parent.getParentAttachment() != null) {
                    List<MapWidgetAttachmentNode> attachments = parent.getAttachments();
                    int from_index = attachments.indexOf(selected);
                    attachments.remove(from_index);

                    List<MapWidgetAttachmentNode> parentAttachments = parent.getParentAttachment().getAttachments();
                    parentAttachments.add(parentAttachments.indexOf(parent) + 1, selected);
                    selected.setParentAttachment(parent.getParentAttachment());

                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    this.resetNeeded = true;
                }
            }

            // Make the selected node a child of the node preceding it
            // [/]            [/]
            // [*]     ===>      [*]
            // [/]            [/]
            if (action == MapPlayerInput.Key.RIGHT) {
                MapWidgetAttachmentNode parent = selected.getParentAttachment();
                if (parent != null) {
                    List<MapWidgetAttachmentNode> attachments = parent.getAttachments();
                    int from_index = attachments.indexOf(selected);
                    int to_index = from_index - 1;
                    if (to_index >= 0 && to_index < attachments.size()) {
                        MapWidgetAttachmentNode new_parent = attachments.get(to_index);
                        attachments.remove(from_index);
                        new_parent.getAttachments().add(selected);
                        selected.setParentAttachment(new_parent);
                        new_parent.setExpanded(true);

                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                        this.resetNeeded = true;
                    }
                }
            }

            // Refresh selected node
            setSelectedNode(selected);
        } else if (event.getKey() == MapPlayerInput.Key.UP) {
            if (this.lastSelIdx > 0) {
                // Focus previous widget
                this.setSelectedIndex(this.lastSelIdx - 1);
            } else if (this.offset > 0) {
                // Shift view one up, if possible, and focus the top widget
                this.lastSelIdx = -1;
                this.updateView(this.offset - 1);
                widgets = this.getVisibleNodes();
                this.setSelectedIndex(0);
            }
        } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
            if (this.lastSelIdx < (widgets.size() - 1)) {
                // Focus next widget
                this.setSelectedIndex(this.lastSelIdx + 1);
            } else {
                // Shift view one down, if the last displayed node is not the last node of the tree
                MapWidgetAttachmentNode lastVisibleNode = widgets.get(widgets.size() - 1);
                MapWidgetAttachmentNode lastTreeNode = findLastNode(root);
                if (lastVisibleNode != lastTreeNode) {
                    this.lastSelIdx = -1;
                    this.updateView(this.offset + 1);
                    widgets = this.getVisibleNodes();
                    this.setSelectedIndex(widgets.size() - 1);
                }
            }
        } else {
            // Let normal navigation handle it
            super.onKeyPressed(event);
        }

        // Faster redraw
        if (this.resetNeeded) {
            this.updateView(this.offset);
            widgets = this.getVisibleNodes();
        }

        // Changed selected node during all this?
        if (this.getSelectedNode() != currentlySelected) {
            this.getEditor().onSelectedNodeChanged();
        }
    }

    public void setSelectedNode(MapWidgetAttachmentNode node) {
        boolean changed = (this.getSelectedNode() != node);
        int new_index = findIndexOf(node) - this.offset;
        if (new_index != this.lastSelIdx) {
            this.resetNeeded = true;
        }

        // Index too high, scroll
        int a = new_index - this.getWidgetCount() + 1;
        if (a > 0) {
            this.offset += a;
            new_index -= a;
        }

        // Index too low, scroll
        if (new_index < 0) {
            this.offset += new_index;
            new_index = 0;
        }

        // Select it
        this.setSelectedIndex(new_index);

        // Event
        if (changed) {
            this.getEditor().onSelectedNodeChanged();
        }
    }

    public AttachmentEditor getEditor() {
        return (AttachmentEditor) super.getDisplay();
    }

    public void updateView() {
        this.resetNeeded = true;
    }

    public void updateView(int offset) {
        this.offset = offset;
        this.root.setEditorOption("scrollOffset", 0, offset);

        this.clearWidgets();
        UpdateViewOp op = new UpdateViewOp();
        op.offset = this.offset;
        op.count = this.count;
        op.num_visible_nodes = 0;
        op.col = 0;
        op.row = 0;
        op.min_col = Integer.MAX_VALUE;
        op.max_col = 0;
        this.column_offset = 0;
        this.updateView(this.root, op);

        // If less nodes are visible than there are slots, force offset to be 0 so root is at the top
        if (op.num_visible_nodes <= this.count && this.offset > 0) {
            int new_offset = 0;
            int new_selidx = this.lastSelIdx - (new_offset - offset);
            this.updateView(new_offset);
            this.setSelectedIndex(new_selidx);
            return;
        }

        // Otherwise, if not all slots are filled with widgets, then we scrolled out of range
        // Check that more are available than are displayed, and if so, scroll to fill
        if (op.count > 0 && op.num_visible_nodes > this.count) {
            int new_offset = op.num_visible_nodes - this.count;
            int new_selidx = this.lastSelIdx - (new_offset - offset);
            this.updateView(new_offset);
            this.setSelectedIndex(new_selidx);
            return;
        }

        this.resetNeeded = false;
        if (this.lastSelIdx >= 0 && this.getWidgetCount() > 0) {
            if (this.lastSelIdx >= this.getWidgetCount()) {
                this.setSelectedIndex(this.getWidgetCount() - 1);
            } else {
                this.setSelectedIndex(this.lastSelIdx);
            }
        }
    }

    private void updateView(MapWidgetAttachmentNode node, UpdateViewOp op) {
        op.num_visible_nodes++;
        if (op.offset > 0) {
            op.offset--;
        } else if (op.count > 0) {
            if (op.col < op.min_col) {
                op.min_col = op.col;
            }
            if (op.col > op.max_col) {
                op.max_col = op.col;
            }
            node.setCell(op.col, op.row);
            node.setPosition(0, this.getWidgets().size() * 17);
            this.addWidget(node);
            op.count--;
        } else {
            return;
        }
        op.row++;
        if (node.isExpanded()) {
            op.col++;
            for (MapWidgetAttachmentNode childAttachment : node.getAttachments()) {
                updateView(childAttachment, op);
            }
            op.col--;
        }
    }

    private void setSelectedIndex(int newIndex) {
        if (this.lastSelIdx != newIndex) {
            this.lastSelIdx = newIndex;
            this.root.setEditorOption("selectedIndex", 0, newIndex);
        }
        if (this.lastSelIdx >= 0 && this.lastSelIdx < this.getWidgetCount()) {
            this.computeColumnOffset();
            this.getWidget(this.lastSelIdx).focus();
        }
    }

    // Recomputes the column offset that makes sure the selected node column is < MAX_VISIBLE_DEPTH
    private void computeColumnOffset() {
        // Compute the new column offset, keeping the currently set column offset into account
        MapWidgetAttachmentNode selectedNode = (MapWidgetAttachmentNode) this.getWidget(this.lastSelIdx);
        int new_column_offset = MAX_VISIBLE_DEPTH - (selectedNode.getCellColumn() - this.column_offset);
        if (new_column_offset > 0) {
            new_column_offset = 0;
        }

        // If different, re-render
        if (new_column_offset != this.column_offset) {
            for (MapWidgetAttachmentNode node : this.getVisibleNodes()) {
                node.setCell(node.getCellColumn() - this.column_offset + new_column_offset, node.getCellRow());
                node.invalidate();
            }
            this.column_offset = new_column_offset;
        }
    }

    private static MapWidgetAttachmentNode findLastNode(MapWidgetAttachmentNode node) {
        List<MapWidgetAttachmentNode> children = node.getAttachments();
        if (!node.isExpanded() || children.isEmpty()) {
            return node;
        } else {
            return findLastNode(children.get(children.size()-1));
        }
    }

    private int findIndexOf(MapWidgetAttachmentNode node) {
        FindIndexOp op = new FindIndexOp();
        op.index = 0;
        op.node = node;
        return searchForNode(this.root, op) ? op.index : -1;
    }

    private static boolean searchForNode(MapWidgetAttachmentNode parent, FindIndexOp op) {
        if (parent == op.node) {
            return true;
        }
        op.index++;
        if (parent.isExpanded()) {
            for (MapWidgetAttachmentNode child : parent.getAttachments()) {
                if (searchForNode(child, op)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class FindIndexOp {
        public int index;
        MapWidgetAttachmentNode node;
    }

    private static class UpdateViewOp {
        public int offset;
        public int count;
        public int num_visible_nodes;
        public int col, row;
        public int max_col, min_col;
    }
}
