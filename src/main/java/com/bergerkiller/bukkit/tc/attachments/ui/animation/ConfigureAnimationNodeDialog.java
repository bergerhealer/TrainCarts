package com.bergerkiller.bukkit.tc.attachments.ui.animation;

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
    private AnimationNode _node;

    public ConfigureAnimationNodeDialog(AnimationNode node) {
        this.setBackgroundColor(MapColorPalette.COLOR_GREEN);
        this._node = node;
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
     * Called when this node needs to be deleted from the array
     */
    public void onDelete() {
    }

    public AnimationNode getNode() {
        return this._node;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // Note: relative to view widget
        // Adjust own bounds to be relative to where parent is at
        this.setBounds(5 - this.parent.getX(), 15 - this.parent.getY(), 105, 88);
        
        int slider_width = 70;
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
                updateNode(ChangeMode.ACTIVE, getNode().isActive() ? 0.0 : 1.0);
                updateView();
            }

            private void updateView() {
                setIcon(getNode().isActive() ?
                        "attachments/anim_node_active.png" : "attachments/anim_node_inactive.png");
                setTooltip(getNode().isActive() ? "Active" : "Inactive");
            }
        }.setPosition(x_offset + 7, y_offset));

        // Duplicate node below this one node
        MapWidget duplicateButton = this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onDuplicate();
                ConfigureAnimationNodeDialog.this.close();
            }
        }.setTooltip("Duplicate").setIcon("attachments/anim_node_duplicate.png").setPosition(x_offset + 30, y_offset));

        // Delete the node
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onDelete();
                ConfigureAnimationNodeDialog.this.close();
            }
        }.setTooltip("Delete").setIcon("attachments/anim_node_delete.png").setPosition(x_offset + slider_width - 17, y_offset));

        y_offset += 12;
        
        this.addWidget(new MapWidgetNumberBox() { // Delta Time
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getNode().getDuration());
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.DURATION, this.getValue());
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

        this.addWidget(new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getNode().getPosition().getX());
                this.focus();
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
                this.setValue(getNode().getPosition().getY());
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
                this.setValue(getNode().getPosition().getZ());
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
                this.setValue(getNode().getRotationVector().getX());
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
                this.setValue(getNode().getRotationVector().getY());
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
                this.setValue(getNode().getRotationVector().getZ());
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.ROT_Z, this.getValue());
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Roll");
        y_offset += y_step;
    }

    private void updateNode(ChangeMode mode, double new_value) {
        Vector pos = this.getNode().getPosition().clone();
        Vector rot = this.getNode().getRotationVector().clone();
        boolean active = this.getNode().isActive();
        double duration = this.getNode().getDuration();
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
        this._node = new AnimationNode(pos, rot, active, duration);
        this.onChanged();
    }

    private static enum ChangeMode {
        POS_X, POS_Y, POS_Z,
        ROT_X, ROT_Y, ROT_Z,
        DURATION, ACTIVE
    }
}
