package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.List;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.CommonSounds;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;

/**
 * The tree view storing all the map widget attachment nodes.
 * Manages the selection and adding/removing of nodes in the tree.
 * Also manages the view area scrolling for larger trees.
 */
public abstract class MapWidgetAttachmentTree extends MapWidget {
    private MapWidgetAttachmentNode root = new MapWidgetAttachmentNode();
    private int offset = 0;
    private int count = 6;
    private int lastSelIdx = 0;
    private boolean resetNeeded;
    private AttachmentModel model = null;

    public void setModel(AttachmentModel model) {
        this.model = model;
        this.root = new MapWidgetAttachmentNode();
        this.root.loadConfig(model.getConfig());
        this.updateView(0);
    }

    public void updateModel() {
        this.model.update(root.getFullConfig());
    }

    public abstract void onMenuOpen(MapWidgetAttachmentNode node, MapWidgetAttachmentNode.MenuItem menu);

    public MapWidgetAttachmentNode getRoot() {
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

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        List<MapWidgetAttachmentNode> widgets = CommonUtil.unsafeCast(this.getWidgets());
        if (widgets.isEmpty()) {
            return;
        }

        this.lastSelIdx = widgets.indexOf(this.getNextInputWidget());
        if (this.lastSelIdx >= 0 && this.lastSelIdx < widgets.size() && widgets.get(this.lastSelIdx).isChangingOrder()) {
            MapWidgetAttachmentNode selected = widgets.get(this.lastSelIdx);
            int globSelIdxBefore = findIndexOf(selected);

            // Complete changing order of widget
            if (event.getKey() == MapPlayerInput.Key.ENTER || event.getKey() == MapPlayerInput.Key.BACK) {
                selected.setChangingOrder(false);
                display.playSound(CommonSounds.CLICK);
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

                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                        this.resetNeeded = true;
                    }
                }
            }

            // Refresh selected index after the modifications
            this.lastSelIdx += findIndexOf(selected) - globSelIdxBefore;

            // Index too high, scroll
            int a = this.lastSelIdx - this.getWidgetCount() + 1;
            if (a > 0) {
                this.offset += a;
                this.lastSelIdx -= a;
            }

            // Index too low, scroll
            if (this.lastSelIdx < 0) {
                this.offset += this.lastSelIdx;
                this.lastSelIdx = 0;
            }

        } else if (event.getKey() == MapPlayerInput.Key.UP) {
            if (this.lastSelIdx > 0) {
                // Focus previous widget
                this.lastSelIdx--;
                widgets.get(this.lastSelIdx).focus();
            } else if (this.offset > 0) {
                // Shift view one up, if possible, and focus the top widget
                this.lastSelIdx = -1;
                this.updateView(this.offset - 1);
                this.lastSelIdx = 0;
                widgets.get(this.lastSelIdx).focus();
            }
        } else if (event.getKey() == MapPlayerInput.Key.DOWN) {
            if (this.lastSelIdx < (widgets.size() - 1)) {
                // Focus next widget
                this.lastSelIdx++;
                widgets.get(this.lastSelIdx).focus();
            } else {
                // Shift view one down, if possible
                // Check if the last widget displayed is the very last widget in the tree
                MapWidgetAttachmentNode tmp = widgets.get(widgets.size() - 1);
                boolean isLast = true;
                if (tmp.getAttachments().isEmpty()) {
                    while (tmp != null) {
                        MapWidgetAttachmentNode tmpParent = tmp.getParentAttachment();
                        if (tmpParent != null) {
                            List<MapWidgetAttachmentNode> tmpParentCh = tmpParent.getAttachments();
                            if (tmpParentCh.get(tmpParentCh.size() - 1) != tmp) {
                                isLast = false; // Not last child of parent
                                break;
                            }
                        }
                        tmp = tmpParent;
                    }
                } else {
                    isLast = false; // Has children, not last
                }

                // If not last widget, offset the view
                if (!isLast) {
                    this.lastSelIdx = -1;
                    this.updateView(this.offset + 1);
                    this.lastSelIdx = this.getWidgetCount() - 1;
                    this.getWidgets().get(this.lastSelIdx).focus();
                }
            }
        } else {
            // Let normal navigation handle it
            super.onKeyPressed(event);
        }

        // Faster redraw
        if (this.resetNeeded) {
            this.updateView(this.offset);
        }
    }

    public void updateView() {
        this.resetNeeded = true;
    }

    public void updateView(int offset) {
        this.offset = offset;

        this.clearWidgets();
        UpdateViewOp op = new UpdateViewOp();
        op.offset = this.offset;
        op.count = this.count;
        op.col = 0;
        op.row = 0;
        this.updateView(this.root, op);
        this.resetNeeded = false;
        if (this.lastSelIdx >= 0 && this.getWidgetCount() > 0) {
            if (this.lastSelIdx >= this.getWidgetCount()) {
                this.lastSelIdx = this.getWidgetCount() - 1;
            }
            this.getWidget(this.lastSelIdx).focus();
        }
    }

    private void updateView(MapWidgetAttachmentNode node, UpdateViewOp op) {
        node.setCell(op.col, op.row);
        if (op.offset > 0) {
            op.offset--;
        } else if (op.count > 0) {
            node.setPosition(0, this.getWidgets().size() * 17);
            this.addWidget(node);
            op.count--;
        } else {
            return;
        }
        op.row++;
        op.col++;
        for (MapWidgetAttachmentNode childAttachment : node.getAttachments()) {
            updateView(childAttachment, op);
        }
        op.col--;
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
        for (MapWidgetAttachmentNode child : parent.getAttachments()) {
            if (searchForNode(child, op)) {
                return true;
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
        public int col, row;
    }
}
