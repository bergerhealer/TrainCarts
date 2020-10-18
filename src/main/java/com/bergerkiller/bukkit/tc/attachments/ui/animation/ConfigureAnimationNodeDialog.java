package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;

public class ConfigureAnimationNodeDialog extends MapWidgetMenu {
    private final AnimationNode _average;
    private List<Node> _nodes;

    public ConfigureAnimationNodeDialog(List<AnimationNode> nodes) {
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
        this._average = AnimationNode.average(nodes);
        this._nodes = nodes.stream().map(Node::new).collect(Collectors.toList());
    }

    /**
     * Called when the properties of the animation node have changed
     */
    public void onChanged() {
    }

    /**
     * Called when this node needs to be duplicated one down
     */
    public void onDuplicate() {
    }

    /**
     * Called when the order of the nodes should be changed
     */
    public void onReorder() {
    }

    /**
     * Called when this node needs to be deleted from the array
     */
    public void onDelete() {
    }

    /**
     * Called when multi-selection mode should be activated from this node
     */
    public void onMultiSelect() {
    }

    /**
     * Gets the average animation node values that were there
     * when opening the dialog.
     * 
     * @return average
     */
    public AnimationNode getAverage() {
        return this._average;
    }

    /**
     * Gets all the nodes being edited
     * 
     * @return nodes
     */
    public List<AnimationNode> getNodes() {
        return this._nodes.stream().map(n -> n.node).collect(Collectors.toList());
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // Note: relative to view widget
        // Adjust own bounds to be relative to where parent is at
        this.setBounds(5 - this.parent.getX(), 15 - this.parent.getY(), 105, 88);
        
        int slider_width = 71;
        int x_offset = 32;
        int y_offset = 4;
        int y_step = 10;

        // Activate/de-activate the node - checkbox or slider?
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onAttached() {
                super.onAttached();
                this.updateView();
            }

            @Override
            public void onClick() {
                updateNode(ChangeMode.ACTIVE, isCurrentlyActive() ? 0.0 : 1.0);
                updateView();
            }

            private void updateView() {
                boolean active = isCurrentlyActive();
                setIcon(active ?
                        "attachments/anim_node_active.png" : "attachments/anim_node_inactive.png");
                setTooltip(active ? "Active" : "Inactive");
            }

            private boolean isCurrentlyActive() {
                if (_nodes.size() == 1) {
                    return _nodes.get(0).node.isActive();
                } else {
                    int num_active = 0;
                    for (Node n : _nodes) {
                        if (n.node.isActive()) {
                            num_active++;
                        }
                    }
                    return num_active >= (_nodes.size()>>1);
                }
            }
        }.setPosition(x_offset + 7, y_offset));

        // Select a range of animation frames from the currently selected node
        int mtmpx = x_offset + 18;
        final int mtmpx_step = 12;
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onMultiSelect();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Multi-select").setIcon("attachments/anim_node_multiselect.png").setPosition(mtmpx, y_offset);

        // Change the position of one or a group of nodes, moving it up/down
        mtmpx += mtmpx_step;
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onReorder();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Change order").setIcon("attachments/anim_node_reorder.png").setPosition(mtmpx, y_offset);

        // Duplicate node below this one node
        mtmpx += mtmpx_step;
        MapWidget duplicateButton = this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onDuplicate();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Duplicate").setIcon("attachments/anim_node_duplicate.png").setPosition(mtmpx, y_offset);

        // Delete the node
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onDelete();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Delete").setIcon("attachments/anim_node_delete.png").setPosition(x_offset + slider_width - 17, y_offset);

        y_offset += 12;
        
        this.addWidget(new MapWidgetNumberBox() { // Delta Time
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getAverage().getDuration());
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.DURATION, this.getValue());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Delta Time";
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == Key.UP) {
                    // Force the duplicate button to be focused
                    duplicateButton.focus();
                } else {
                    super.onKeyPressed(event);
                }
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Delta T");
        y_offset += y_step;

        MapWidget posXWidget = this.addWidget(new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getAverage().getPosition().getX());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position X-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.POS_X, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Pos.X");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getAverage().getPosition().getY());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Y-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.POS_Y, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Pos.Y");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getAverage().getPosition().getZ());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Position Z-Coordinate";
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.POS_Z, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Pos.Z");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Rotation X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getAverage().getRotationVector().getX());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Pitch";
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.ROT_X, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Pitch");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Rotation Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getAverage().getRotationVector().getY());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Yaw";
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.ROT_Y, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Yaw");
        y_offset += y_step;

        this.addWidget(new MapWidgetNumberBox() { // Rotation Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setValue(getAverage().getRotationVector().getZ());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Rotation Roll";
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.ROT_Z, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Roll");
        y_offset += y_step;

        // Focus the widget we had focused last time the menu was open
        // If -1, select pos x by default
        int initialFocusedIndex = attachment.getEditorOption("animNodeSelectedOption", -1);
        if (initialFocusedIndex >= 0 && initialFocusedIndex < this.getWidgetCount()) {
            this.getWidget(initialFocusedIndex).focus();
        } else {
            posXWidget.focus();
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        super.onKeyPressed(event);

        // Key press may have altered focused widget
        if (display != null) {
            int index = getWidgets().indexOf(display.getFocusedWidget());
            if (index != -1) {
                attachment.setEditorOption("animNodeSelectedOption", -1, index);
            }
        }
    }

    private void updateNode(ChangeMode mode, double new_value) {
        for (Node n : this._nodes) {
            n.update(mode, new_value);
        }
        this.onChanged();
    }

    private static enum ChangeMode {
        POS_X, POS_Y, POS_Z,
        ROT_X, ROT_Y, ROT_Z,
        DURATION, ACTIVE
    }

    private class Node {
        public final AnimationNode original;
        public AnimationNode node;

        public Node(AnimationNode node) {
            this.original = node.clone();
            this.node = node;
        }

        public void update(ChangeMode mode, double new_value) {
            Vector pos = this.node.getPosition().clone();
            Vector rot = this.node.getRotationVector().clone();
            boolean active = this.node.isActive();
            double duration = this.node.getDuration();

            if (_nodes.size() > 1) {
                // Multi select: check difference from average, add to the original value
                Vector opos = original.getPosition();
                Vector orot = original.getRotationVector();
                Vector apos = getAverage().getPosition();
                Vector arot = getAverage().getRotationVector();
                switch (mode) {
                case POS_X: pos.setX(opos.getX() + new_value - apos.getX()); break;
                case POS_Y: pos.setY(opos.getY() + new_value - apos.getY()); break;
                case POS_Z: pos.setZ(opos.getZ() + new_value - apos.getZ()); break;
                case ROT_X: rot.setX(orot.getX() + new_value - arot.getX()); break;
                case ROT_Y: rot.setY(orot.getY() + new_value - arot.getY()); break;
                case ROT_Z: rot.setZ(orot.getZ() + new_value - arot.getZ()); break;
                case DURATION: duration = original.getDuration() + new_value - getAverage().getDuration(); break;
                case ACTIVE: active = (new_value != 0.0); break;
                }
            } else {
                // Single select: update values instantly
                switch (mode) {
                case POS_X: pos.setX(new_value); break;
                case POS_Y: pos.setY(new_value); break;
                case POS_Z: pos.setZ(new_value); break;
                case ROT_X: rot.setX(new_value); break;
                case ROT_Y: rot.setY(new_value); break;
                case ROT_Z: rot.setZ(new_value); break;
                case DURATION: duration = new_value; break;
                case ACTIVE: active = (new_value != 0.0); break;
                }
            }

            this.node = new AnimationNode(pos, rot, active, duration);
        }
    }
}
