package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.List;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
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
        List<MapWidget> widgets = this.getWidgets();
        if (widgets.isEmpty()) {
            return;
        }

        this.lastSelIdx = widgets.indexOf(this.getNextInputWidget());
        if (event.getKey() == MapPlayerInput.Key.UP) {
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
                MapWidgetAttachmentNode tmp = (MapWidgetAttachmentNode) widgets.get(widgets.size() - 1);
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

    private static class UpdateViewOp {
        public int offset;
        public int count;
        public int col, row;
    }
}
