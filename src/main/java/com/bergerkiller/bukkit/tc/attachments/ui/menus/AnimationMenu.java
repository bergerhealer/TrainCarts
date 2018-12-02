package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import java.util.ArrayList;
import java.util.Arrays;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationNode;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.ConfigureAnimationNodeDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.ConfirmAnimationDeleteDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.MapWidgetAnimationView;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class AnimationMenu extends MapWidgetMenu {
    private boolean playForAll = false;
    private final MapWidgetAttachmentNode attachment;
    private final MapWidgetSelectionBox animSelectionBox = new MapWidgetSelectionBox() { // anchor
        @Override
        public void onAttached() {
            super.onAttached();

            for (String name : getAnimRootConfig().getKeys()) {
                this.addItem(name);
            }
            if (!this.getItems().isEmpty()) {
                this.setSelectedItem(this.getItems().get(0));
            }
            this.onSelectedItemChanged();
        }

        @Override
        public void onSelectedItemChanged() {
            boolean menuEnabled = !this.getItems().isEmpty();
            animView.setAnimation(getAnimation());
            animDelete.setEnabled(menuEnabled);
            animConfig.setEnabled(menuEnabled);
            animManyMode.setEnabled(menuEnabled);
            animPlayFwd.setEnabled(menuEnabled);
            animPlayRev.setEnabled(menuEnabled);
        }
    };
    private final MapWidgetAnimationView animView = new MapWidgetAnimationView() {
        @Override
        public void onSelectionActivated() {
            final int node_index = this.getSelectedIndex();
            final AnimationNode node = this.getSelectedNode();
            if (node != null) {
                this.addWidget(new ConfigureAnimationNodeDialog(node) {
                    @Override
                    public void onChanged() {
                        updateAnimationNode(node_index, this.getNode());
                    }

                    @Override
                    public void onDuplicate() {
                        duplicateAnimationNode(node_index);
                    }

                    @Override
                    public void onDelete() {
                        deleteAnimationNode(node_index);
                    }

                    @Override
                    public void onDeactivate() {
                        super.onDeactivate();

                        // Refresh model so that it uses these options in the actual animation
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                });
            }
        }

        @Override
        public void onSelectionChanged() {
            AnimationNode node = this.getSelectedNode();
            if (node != null) {
                previewAnimationNode(this.getSelectedIndex(), node);
            }
        }
    };
    private final MapWidgetSubmitText animNameBox = new MapWidgetSubmitText() {
        @Override
        public void onAttached() {
            super.onAttached();
            setDescription("Enter the animation name");
        }

        @Override
        public void onAccept(String text) {
            createAnimation(text);
        }
    };

    /* =================== Main menu buttons ====================== */
    private final MapWidgetBlinkyButton animDelete = new MapWidgetBlinkyButton() {
        @Override
        public void onClick() {
            AnimationMenu.this.addWidget(new ConfirmAnimationDeleteDialog() {
                @Override
                public void onConfirmDelete() {
                    deleteAnimation();
                }
            });
        }
    };
    private final MapWidgetBlinkyButton animConfig = new MapWidgetBlinkyButton() {
        @Override
        public void onClick() {
            
        }
    };
    private final MapWidgetBlinkyButton animManyMode = new MapWidgetBlinkyButton() {
        @Override
        public void onAttached() {
            this.updateIcon();
        }

        @Override
        public void onClick() {
            playForAll = !playForAll;
            this.updateIcon();
        }

        private void updateIcon() {
            setIcon(playForAll ? "attachments/anim_many.png" : "attachments/anim_single.png");
        }
    };
    private final MapWidgetBlinkyButton animPlayRev = new MapWidgetBlinkyButton() {
        @Override
        public void onClick() {
            playAnimation(true, false);
        }

        @Override
        public void onClickHold() {
            playAnimation(true, true);
        }

        @Override
        public void onClickHoldRelease() {
            this.onClick(); // plays once to restore
        }
    };
    private final MapWidgetBlinkyButton animPlayFwd = new MapWidgetBlinkyButton() {
        @Override
        public void onClick() {
            playAnimation(false, false);
        }

        @Override
        public void onClickHold() {
            playAnimation(false, true);
        }

        @Override
        public void onClickHoldRelease() {
            this.onClick(); // plays once to restore
        }
    };
    /* ============================================================== */

    public AnimationMenu(MapWidgetAttachmentNode attachment) {
        this.attachment = attachment;
        this.setBounds(5, 15, 118, 108);
        this.setBackgroundColor(MapColorPalette.COLOR_CYAN);
    }

    @Override
    public void onAttached() {
        super.onAttached();

        int top_menu_x = 3;
        int top_menu_y = 3;

        this.addWidget(this.animSelectionBox.setBounds(top_menu_x, top_menu_y, getWidth()-6, 11));
        top_menu_x = 8;
        top_menu_y += 13;

        // Button to create a new animation, first asks for animation name
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onClick() {
                animNameBox.activate();
            }
        }.setIcon("attachments/anim_new.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to delete the currently selected animation (with confirmation)
        this.addWidget(this.animDelete.setIcon("attachments/anim_delete.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to configure the currently selected node (speed, delay, looping)
        this.addWidget(this.animConfig.setIcon("attachments/anim_config.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to switch between playing just this node, or all nodes of the (cart) model
        this.addWidget(this.animManyMode.setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to play the current animation (in reverse)
        this.addWidget(this.animPlayRev.setIcon("attachments/anim_play_rev.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to play the current animation (forwards)
        this.addWidget(this.animPlayFwd.setIcon("attachments/anim_play_fwd.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        top_menu_x = 3;
        top_menu_y += 18;

        this.addWidget(this.animNameBox);
        this.addWidget(this.animView.setBounds(top_menu_x, top_menu_y, getWidth()-2*top_menu_x, 11*6+1));
    }

    /**
     * Plays the selected animation
     * 
     * @param reverse
     * @param looped
     */
    public void playAnimation(boolean reverse, boolean looped) {
        AttachmentEditor editor = (AttachmentEditor) this.getDisplay();
        MinecartMember<?> member = editor.editedCart.getHolder();
        if (member == null) {
            return; // Not loaded.
        }

        AnimationOptions options = new AnimationOptions(this.animSelectionBox.getSelectedItem());
        options.setSpeed(reverse ? -1.0 : 1.0);
        options.setLooped(looped);
        options.setReset(!looped);
        if (this.playForAll) {
            // Target the entire model
            member.playNamedAnimation(options);
        } else {
            // Target only the attachment we are editing
            int[] targetPath = this.attachment.getTargetPath();
            member.playNamedAnimationFor(targetPath, options);
        }
    }

    /**
     * Deletes the currently selected animation
     */
    public void deleteAnimation() {
        String item = this.animSelectionBox.getSelectedItem();
        this.animSelectionBox.removeItem(item);
        this.getAnimRootConfig().remove(item);
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
    }

    /**
     * Creates a new animation and selects it
     * 
     * @param name of the animation
     */
    public void createAnimation(String name) {
        // If the animation by this name already exists, merely select it (don't create)
        if (this.animSelectionBox.getItems().contains(name)) {
            this.animSelectionBox.setSelectedItem(name);
            return;
        }

        // Create a default animation configuration at this node, and save it
        this.setAnimation(new Animation(name,
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=0.0 pitch=0.0 roll=0.0",
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=90.0 pitch=0.0 roll=0.0",
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=180.0 pitch=0.0 roll=0.0",
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=270.0 pitch=0.0 roll=0.0"
        ));
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
    }

    /**
     * Duplicates the node at the index and inserts a clone at index+1.
     * 
     * @param index
     */
    public void duplicateAnimationNode(int index) {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null) {
            return;
        }

        ArrayList<AnimationNode> tmp = new ArrayList<AnimationNode>(Arrays.asList(old_anim.getNodeArray()));
        tmp.add(index+1, tmp.get(index));
        AnimationNode[] new_nodes = LogicUtil.toArray(tmp, AnimationNode.class);
        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimation(replacement);

        animView.setSelectedIndex(index + 1);
    }

    /**
     * Deletes an existing animation node from the animation.
     * If the animation has only one node, the deletion silently fails. (TODO?)
     * 
     * @param index
     */
    public void deleteAnimationNode(int index) {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null || old_anim.getNodeCount() <= 1) {
            return;
        }

        AnimationNode[] new_nodes = LogicUtil.removeArrayElement(old_anim.getNodeArray(), index);
        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimation(replacement);
    }

    /**
     * Updates the value of an existing animation node.
     * Sends a preview update as well
     * 
     * @param index of the node
     * @param node value to replace it with
     */
    public void updateAnimationNode(int index, AnimationNode node) {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null) {
            return;
        }

        // System.out.println("NODE[" + index + "] = " + node.serializeToString());

        AnimationNode[] new_nodes = old_anim.getNodeArray().clone();
        if (index >= 0 && index < new_nodes.length) {
            new_nodes[index] = node;
        }
        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimation(replacement);

        previewAnimationNode(index, node);
    }

    /**
     * Animates the model of the edited cart to display the positions
     * of the animation node at an index.
     * 
     * @param index
     * @param node
     */
    public void previewAnimationNode(int index, AnimationNode node) {
        AttachmentEditor editor = (AttachmentEditor) this.getDisplay();
        MinecartMember<?> member = editor.editedCart.getHolder();
        if (member == null) {
            return; // Not loaded.
        }

        // When inactive, blink the node on and off to display it's position that way
        AnimationNode[] nodes;
        if (node.isActive()) {
            nodes = new AnimationNode[] {node};
        } else {
            nodes = new AnimationNode[] {
                    new AnimationNode(node.getPosition(), node.getRotationVector(), true, 0.5),
                    new AnimationNode(node.getPosition(), node.getRotationVector(), false, 0.5),
            };
        }

        Animation anim_preview = new Animation("DUMMY_DO_NOT_USE", nodes);
        anim_preview.getOptions().setReset(true);
        anim_preview.getOptions().setLooped(true);
        member.playAnimationFor(this.attachment.getTargetPath(), anim_preview);
    }

    public Animation getAnimation() {
        String item = this.animSelectionBox.getSelectedItem();
        return (item == null) ? null : Animation.loadFromConfig(getAnimRootConfig().getNode(item));
    }

    public void setAnimation(Animation animation) {
        // Save to configuration
        animation.saveToParentConfig(getAnimRootConfig());

        // Add item to selection box if not exists, otherwise refresh view
        String name = animation.getOptions().getName();
        if (this.animSelectionBox.getItems().contains(name)) {
            this.animView.setAnimation(animation);
        } else {
            this.animSelectionBox.addItem(name);
            this.animSelectionBox.setSelectedItem(name);
        }
    }

    public ConfigurationNode getAnimRootConfig() {
        return this.attachment.getConfig().getNode("animations");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}
