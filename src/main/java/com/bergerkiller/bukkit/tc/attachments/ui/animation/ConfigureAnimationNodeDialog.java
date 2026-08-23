package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationEasing;
import com.bergerkiller.bukkit.tc.attachments.ui.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;

public class ConfigureAnimationNodeDialog extends MapWidgetMenu {
    private final MapWidgetScroller _scroller = new MapWidgetScroller();
    private final AnimationNode _average;
    private List<Node> _nodes;
    private MapWidgetSubmitText sceneMarkerSubmit = null;
    private MapWidgetSelectionBox easingSelectionBox;

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
     * Called when the selected nodes' contents need to be copied to the player clipboard
     */
    public void onCopy() {
    }

    /**
     * Called when player clipboard contents should be pasted below the selected node(s)
     */
    public void onPaste() {
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

    /**
     * Modified method to add the label to the scroller instead of the
     * default behavior of adding it to the dialog itself.
     * @param x
     * @param y
     * @param text
     */
    @Override
    public void addLabel(int x, int y, String text) {
        MapWidgetText label = new MapWidgetText();
        label.setFont(MapFont.TINY);
        label.setText(text);
        label.setPosition(x, y);
        label.setColor(MapColorPalette.getSpecular(this.labelColor, 0.5f));
        _scroller.addContainerWidget(label);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // Note: relative to view widget
        // Adjust own bounds to be relative to where parent is at
        this.setBounds(5 - this.parent.getX(), 15 - this.parent.getY(), 105, 88);

        // Initialize scroller
        this._scroller.setBounds(0, 2, getWidth(), getHeight() - 5);
        this._scroller.setScrollPadding(20);
        this.addWidget(this._scroller);
        
        int slider_width = 72;
        int x_offset = 31;
        int y_offset = 4;
        int y_step = 10;
        int mtmpx = x_offset - 25;
        final int mtmpx_step = 12;

        // Assign a scene marker to this node, so the animation can be played from this node onwards
        // Clicking will open an anvil dialog to enter a marker name - or empty to clear it
        MapWidgetSceneBlinkyButton sceneMarkerButton = _scroller.addContainerWidget(new MapWidgetSceneBlinkyButton());
        sceneMarkerButton.setTooltip("Scene marker").setPosition(mtmpx, y_offset);
        sceneMarkerSubmit = this.addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAttached() {
                super.onAttached();
                setDescription("Enter a scene start marker name\nPut empty space to remove");
            }

            @Override
            public ChatText getTitle() {
                return ChatText.fromMessage("Enter marker name");
            }

            @Override
            public void onAccept(String text) {
                updateScene(text);
                sceneMarkerButton.updateIcon();
            }
        });

        // Activate/de-activate the node - checkbox or slider?
        mtmpx += mtmpx_step;
        _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
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
                    return num_active >= (_nodes.size() >> 1);
                }
            }
        }.setPosition(mtmpx, y_offset));

        // Select a range of animation frames from the currently selected node
        mtmpx += mtmpx_step;
        _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onMultiSelect();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Multi-select").setIcon("attachments/anim_node_multiselect.png").setPosition(mtmpx, y_offset);

        // Change the position of one or a group of nodes, moving it up/down
        mtmpx += mtmpx_step;
        _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onReorder();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Change order").setIcon("attachments/anim_node_reorder.png").setPosition(mtmpx, y_offset);

        // Copy selected nodes to the clipboard of the player
        mtmpx += mtmpx_step;
        _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onActivate() {
                this.onClick(); // Disable extinquish sfx
            }

            @Override
            public void onClick() {
                onCopy();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Copy to Clipboard").setIcon("attachments/anim_node_copy.png").setPosition(mtmpx, y_offset);

        // Paste clipboard contents of the player below the selected nodes
        mtmpx += mtmpx_step;
        _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onAttached() {
                super.onAttached();

                boolean hasClipboard = false;
                for (Player player : display.getOwners()) {
                    if (display.isControlling(player)) {
                        hasClipboard |= AnimationNodeClipboard.hasClipboard(player);
                    }
                }
                this.setEnabled(hasClipboard);
            }

            @Override
            public void onActivate() {
                this.onClick(); // Disable extinquish sfx
            }

            @Override
            public void onClick() {
                onPaste();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Paste from Clipboard").setIcon("attachments/anim_node_paste.png").setPosition(mtmpx, y_offset);

        // Duplicate node below this one node
        mtmpx += mtmpx_step;
        MapWidget duplicateButton = _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onActivate() {
                this.onClick(); // Disable extinquish sfx
            }

            @Override
            public void onClick() {
                onDuplicate();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Duplicate").setIcon("attachments/anim_node_duplicate.png").setPosition(mtmpx, y_offset);

        // Delete the node
        mtmpx += mtmpx_step;
        _scroller.addContainerWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                onDelete();
                ConfigureAnimationNodeDialog.this.close();
            }
        }).setTooltip("Delete").setIcon("attachments/anim_node_delete.png").setPosition(mtmpx, y_offset);

        y_offset += 12;

        // Has to be saved in a variable so it can be updated from the animation ease dialog
        easingSelectionBox = _scroller.addContainerWidget(new MapWidgetSelectionBox() { // Easing
            private final MapWidgetTooltip tooltip = new MapWidgetTooltip();

            @Override
            public void onAttached() {
                super.onAttached();
                Arrays.stream(AnimationEasing.EasingType.values())
                        .map(Enum::name)
                        .forEach(this::addItem);

                // Average just selects first easing, because taking the average doesn't help the user
                int index = AnimationEasing.EasingType.getEasingType(getAverage().getEasing()).ordinal();

                this.setSelectedIndex(index);
                this.setFont(MapFont.TINY);

                onSelectedItemChanged(); // Call manually to set correct tooltip text
            }

            @Override
            public void onFocus() {
                super.onFocus();
                this.addWidget(this.tooltip);
            }

            @Override
            public void onBlur() {
                super.onBlur();
                this.removeWidget(this.tooltip);
            }

            @Override
            public void onSelectedItemChanged() {
                AnimationEasing.EasingType selected = AnimationEasing.EasingType.valueOf(this.getSelectedItem());

                if (selected.isPreset()) { // Only update when it's a preset
                    tooltip.setText("Enter [space] to view");
                    updateEasing(selected.getEasing(), false);
                } else {
                    tooltip.setText("Enter [space] to edit");
                }
            }

            @Override
            public void onKeyPressed(MapKeyEvent event) {
                if (event.getKey() == Key.UP) {
                    // Force the duplicate button to be focused
                    duplicateButton.focus();
                } else {
                    super.onKeyPressed(event);
                }

                if (event.getKey() == Key.ENTER) {
                    openAnimationEaseDialog();
                }
            }
        });
        easingSelectionBox.setBounds(x_offset, y_offset, slider_width, 9);

        addLabel(5, y_offset + 3, "Easing");
        y_offset += y_step;

        _scroller.addContainerWidget(new MapWidgetNumberBox() { // Delta Time
            @Override
            public void onAttached() {
                super.onAttached();
                this.setInitialValue(getAverage().getDuration());
            }

            @Override
            public void onValueChanged() {
                updateNode(ChangeMode.DURATION, this.getValue());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Delta Time";
            }
        }).setBounds(x_offset, y_offset, slider_width, 9);
        addLabel(5, y_offset + 3, "Delta T");
        y_offset += y_step;

        MapWidget posXWidget = _scroller.addContainerWidget(new MapWidgetNumberBox() { // Position X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setInitialValue(getAverage().getPosition().getX());
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

        _scroller.addContainerWidget(new MapWidgetNumberBox() { // Position Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setInitialValue(getAverage().getPosition().getY());
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

        _scroller.addContainerWidget(new MapWidgetNumberBox() { // Position Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setInitialValue(getAverage().getPosition().getZ());
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

        _scroller.addContainerWidget(new MapWidgetNumberBox() { // Rotation X
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setInitialValue(getAverage().getRotationVector().getX());
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

        _scroller.addContainerWidget(new MapWidgetNumberBox() { // Rotation Y
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setInitialValue(getAverage().getRotationVector().getY());
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

        _scroller.addContainerWidget(new MapWidgetNumberBox() { // Rotation Z
            @Override
            public void onAttached() {
                super.onAttached();
                this.setIncrement(0.1);
                this.setInitialValue(getAverage().getRotationVector().getZ());
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

        // Focus the widget we had focused last time the menu was open
        // If -1, select pos x by default
        int initialFocusedIndex = attachment.getEditorOption("animNodeSelectedOption", -1);
        if (initialFocusedIndex >= 0 && initialFocusedIndex < _scroller.getWidgetCount()) {
            _scroller.getWidget(initialFocusedIndex).focus();
        } else {
            posXWidget.focus();
        }
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        super.onKeyPressed(event);

        // Key press may have altered focused widget
        if (display != null) {
            int index = _scroller.getWidgets().indexOf(display.getFocusedWidget());
            if (index != -1) {
                attachment.setEditorOption("animNodeSelectedOption", -1, index);
            }
        }
    }

    private void openAnimationEaseDialog() {
        this.addWidget(new ConfigureAnimationEaseDialog()).setAttachment(attachment);
    }

    private void updateNode(ChangeMode mode, double new_value) {
        for (Node n : this._nodes) {
            n.update(mode, new_value);
        }
        this.onChanged();
    }

    private void updateScene(String newSceneName) {
        for (int i = 0; i < this._nodes.size(); i++) {
            if (i == 0) {
                this._nodes.get(i).updateScene(newSceneName);
            } else {
                this._nodes.get(i).updateScene(null);
            }
        }
        this.onChanged();
    }

    private void updateEasing(AnimationEasing newEasing, boolean shouldRefresh) {
        for (Node node : this._nodes) {
            node.updateEasing(newEasing);
        }
        this.onChanged();

        if (shouldRefresh)
            refreshEasingSelectionBox();
    }

    /**
     * Sets the selected index of the easing selection box to the easing type found by using the 2 points.
     * If no type was found, it uses {@link AnimationEasing.EasingType#CUSTOM}.
     */
    private void refreshEasingSelectionBox() {
        if (easingSelectionBox == null) {
            return;
        }

        AnimationEasing.EasingType type = AnimationEasing.EasingType.getEasingType(_nodes.get(0).node.getEasing());
        easingSelectionBox.setSelectedIndex(type.ordinal());
    }

    private enum ChangeMode {
        POS_X, POS_Y, POS_Z,
        ROT_X, ROT_Y, ROT_Z,
        DURATION, ACTIVE;
    }

    private class Node {
        public final AnimationNode original;
        public AnimationNode node;

        public Node(AnimationNode node) {
            this.original = node.clone();
            this.node = node;
        }

        public void updateScene(String newSceneName) {
            this.node = this.node.setSceneMarker(newSceneName);
        }

        public void updateEasing(AnimationEasing newEasing) {
            this.node = this.node.setEasing(newEasing);
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

            this.node = new AnimationNode(pos, rot, active, duration, this.node.getSceneMarker(), this.node.getEasing());
        }
    }

    private class MapWidgetSceneBlinkyButton extends MapWidgetBlinkyButton {

        @Override
        public void onAttached() {
            updateIcon();
        }

        @Override
        public void onClick() {
            sceneMarkerSubmit.activate();
        }

        public void updateIcon() {
            if (!_nodes.isEmpty() && _nodes.get(0).node.hasSceneMarker()) {
                setIcon("attachments/anim_node_scene_set.png");
            } else {
                setIcon("attachments/anim_node_scene.png");
            }
        }
    }

    private class ConfigureAnimationEaseDialog extends MapWidgetMenu {

        // We need to save the number boxes in variables because they have a
        // bidirectional association with the control points
        private MapWidgetNumberBox p1x;
        private MapWidgetNumberBox p1y;
        private MapWidgetNumberBox p2x;
        private MapWidgetNumberBox p2y;

        public ConfigureAnimationEaseDialog() {
            this.setBackgroundColor(MapColorPalette.COLOR_PURPLE);
            labelColor = MapColorPalette.COLOR_PURPLE;
        }

        private void updatePoint1(double x, double y) {
            p1x.setInitialValue(x);
            p1y.setInitialValue(y);
        }

        private void updatePoint2(double x, double y) {
            p2x.setInitialValue(x);
            p2y.setInitialValue(y);
        }

        @Override
        public void onAttached() {
            super.onAttached();
            this.setBounds(-6, 30, 120, 63);

            final MapWidgetCubicBezier.MapWidgetControlPoint controlPoint1 = new MapWidgetCubicBezier.MapWidgetControlPoint() {
                @Override
                public void onValueChanged() {
                    updatePoint1(x(), y());
                    updateEasing(this.getBezierParent().getEasing(), true);
                }
            };
            controlPoint1.setColor(MapColorPalette.COLOR_BLUE);

            final MapWidgetCubicBezier.MapWidgetControlPoint controlPoint2 = new MapWidgetCubicBezier.MapWidgetControlPoint() {
                @Override
                public void onValueChanged() {
                    updatePoint2(x(), y());
                    updateEasing(this.getBezierParent().getEasing(), true);
                }
            };
            controlPoint2.setColor(MapColorPalette.COLOR_RED);

            final MapWidgetCubicBezier cubicBezier = this.addWidget(new MapWidgetCubicBezier(controlPoint1, controlPoint2));
            cubicBezier.setBounds(3, 3, 57);

            // Shows which number boxes change which point
            addLabel(75, 6, "Point 1");
            addLabel(75, 35, "Point 2");

            // Point 1 x
            p1x = this.addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    setIncrement(0.01);
                    setRange(0.0, 1.0);

                    setInitialValue(cubicBezier.getControlPoint1().x());
                }

                @Override
                public void onValueChanged() {
                    cubicBezier.getControlPoint1().setInitialPoint(
                            (float) getValue(),
                            cubicBezier.getControlPoint1().y()
                    );
                    updateEasing(cubicBezier.getEasing(), true);
                }
            });
            p1x.setBounds(66, 12, 52, 9);
            addLabel(62, 14, "x");

            // Point 1 y
            p1y = this.addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    setIncrement(0.01);
                    setRange(0.0, 1.0);
                    setInitialValue(cubicBezier.getControlPoint1().y());
                }

                @Override
                public void onValueChanged() {
                    cubicBezier.getControlPoint1().setInitialPoint(
                            cubicBezier.getControlPoint1().x(),
                            (float) getValue()
                    );
                    updateEasing(cubicBezier.getEasing(), true);
                }
            });
            p1y.setBounds(66, 22, 52, 9);
            addLabel(62, 24, "y");

            // Point 2 x
            p2x = this.addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    setIncrement(0.01);
                    setRange(0.0, 1.0);
                    setInitialValue(cubicBezier.getControlPoint2().x());
                }

                @Override
                public void onValueChanged() {
                    cubicBezier.getControlPoint2().setInitialPoint(
                            (float) getValue(),
                            cubicBezier.getControlPoint2().y()
                    );
                    updateEasing(cubicBezier.getEasing(), true);
                }
            });
            p2x.setBounds(66, 41, 52, 9);
            addLabel(62, 43, "x");

            // Point 2 y
            p2y = this.addWidget(new MapWidgetNumberBox() {
                @Override
                public void onAttached() {
                    super.onAttached();
                    setIncrement(0.01);
                    setRange(0.0, 1.0);
                    setInitialValue(cubicBezier.getControlPoint2().y());
                }

                @Override
                public void onValueChanged() {
                    cubicBezier.getControlPoint2().setInitialPoint(
                            cubicBezier.getControlPoint2().x(),
                            (float) getValue()
                    );
                    updateEasing(cubicBezier.getEasing(), true);
                }
            });
            p2y.setBounds(66, 51, 52, 9);
            addLabel(62, 53, "y");

            // Set values of number boxes directly to prevent overflowing digits
            AnimationEasing easing = _nodes.get(0).node.getEasing();

            if (easing == null) {
                easing = AnimationEasing.EasingType.LINEAR.getEasing();
            }

            // Initialize the graph directly.
            // setInitialPoint() does not invoke onValueChanged().
            controlPoint1.setInitialPoint(easing.getX1(), easing.getY1());
            controlPoint2.setInitialPoint(easing.getX2(), easing.getY2());

            // Initialize the number boxes without invoking their change callbacks.
            p1x.setInitialValue(easing.getX1());
            p1y.setInitialValue(easing.getY1());
            p2x.setInitialValue(easing.getX2());
            p2y.setInitialValue(easing.getY2());
        }

    }
}

