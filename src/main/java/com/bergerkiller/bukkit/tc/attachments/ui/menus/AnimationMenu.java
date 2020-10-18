package com.bergerkiller.bukkit.tc.attachments.ui.menus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import com.bergerkiller.bukkit.tc.attachments.ui.animation.ConfigureAnimationDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.ConfigureAnimationNodeDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.ConfirmAnimationDeleteDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.animation.MapWidgetAnimationView;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

public class AnimationMenu extends MapWidgetMenu {
    private boolean playForAll = false;
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
            animView.setAnimation(loadAnimation());
            animDelete.setEnabled(menuEnabled);
            animConfig.setEnabled(menuEnabled);
            animManyMode.setEnabled(menuEnabled);
            animPlayFwd.setEnabled(menuEnabled);
            animPlayRev.setEnabled(menuEnabled);
        }

        @Override
        public void onActivate() {
            // Rename current animation
            if (!this.getItems().isEmpty()) {
                animRenameBox.activate();
            }
        }
    };
    private final MapWidgetAnimationView animView = new MapWidgetAnimationView() {
        @Override
        public void onSelectionActivated() {
            List<AnimationNode> nodes = this.getSelectedNodes();
            if (!nodes.isEmpty()) {
                this.addWidget(new ConfigureAnimationNodeDialog(nodes) {
                    @Override
                    public void onChanged() {
                        updateAnimationNodes(getNodes());
                    }

                    @Override
                    public void onMultiSelect() {
                        startMultiSelect();
                    }

                    @Override
                    public void onReorder() {
                        startReordering();
                    }

                    @Override
                    public void onDuplicate() {
                        duplicateAnimationNodes();
                    }

                    @Override
                    public void onDelete() {
                        deleteAnimationNodes();
                    }

                    @Override
                    public void onDeactivate() {
                        super.onDeactivate();

                        // Refresh model so that it uses these options in the actual animation
                        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    }
                }).setAttachment(attachment);
            }
        }

        @Override
        public void onSelectionChanged() {
            AnimationNode node = this.getSelectedNode();
            if (node != null) {
                previewAnimationNode(this.getSelectedIndex(), node);
            }
        }

        @Override
        public void onReorder(int offset) {
            moveAnimationNodes(offset);
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
    private final MapWidgetSubmitText animRenameBox = new MapWidgetSubmitText() {
        @Override
        public void onAttached() {
            super.onAttached();
            setDescription("Enter the new animation name");
        }

        @Override
        public void onAccept(String text) {
            renameAnimation(text);
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
            ConfigureAnimationDialog dialog = new ConfigureAnimationDialog(AnimationMenu.this);
            dialog.setAttachment(attachment);
            AnimationMenu.this.addWidget(dialog);
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
            setTooltip(playForAll ? "Play all" : "Play self");
        }
    };
    private final MapWidgetBlinkyButton animPlayRev = new MapWidgetBlinkyButton() {
        @Override
        public void onAttached() {
            super.onAttached();
            this.setRepeatClickEnabled(true);
        }

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
        public void onAttached() {
            super.onAttached();
            this.setRepeatClickEnabled(true);
        }

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

    public AnimationMenu() {
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
        }.setTooltip("New animation").setIcon("attachments/anim_new.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to delete the currently selected animation (with confirmation)
        this.addWidget(this.animDelete.setTooltip("Delete animation").setIcon("attachments/anim_delete.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to configure the currently selected node (speed, delay, looping)
        this.addWidget(this.animConfig.setTooltip("Configure").setIcon("attachments/anim_config.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to switch between playing just this node, or all nodes of the (cart) model
        this.addWidget(this.animManyMode.setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to play the current animation (in reverse)
        this.addWidget(this.animPlayRev.setTooltip("Play in reverse").setIcon("attachments/anim_play_rev.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        // Button to play the current animation (forwards)
        this.addWidget(this.animPlayFwd.setTooltip("Play forwards").setIcon("attachments/anim_play_fwd.png").setPosition(top_menu_x, top_menu_y));
        top_menu_x += 17;

        top_menu_x = 3;
        top_menu_y += 18;

        this.addWidget(this.animNameBox);
        this.addWidget(this.animRenameBox);
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
     * Changes the name of the currently selected animation
     * 
     * @param newName
     */
    public void renameAnimation(String newName) {
        // Ignore if taken
        if (this.animSelectionBox.getItems().contains(newName)) {
            return;
        }

        // Change name
        Animation anim = this.getAnimation().clone();
        anim.getOptions().setName(newName);
        this.setAnimation(anim);
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

        // Save new animation to configuration
        Animation newAnimation = new Animation(name,
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=0.0 pitch=0.0 roll=0.0",
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=90.0 pitch=0.0 roll=0.0",
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=180.0 pitch=0.0 roll=0.0",
                "t=0.25 x=0.0 y=0.0 z=0.0 yaw=270.0 pitch=0.0 roll=0.0"
        );
        newAnimation.saveToParentConfig(getAnimRootConfig());

        // Add item to select box, then select it
        this.animSelectionBox.addItem(name);
        this.animSelectionBox.setSelectedItem(name);

        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
    }

    /**
     * Duplicates the node at the index and inserts a clone at index+1.
     * 
     * @param index
     */
    public void duplicateAnimationNodes() {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null) {
            return;
        }

        int start = this.animView.getSelectionStart();
        int end = this.animView.getSelectionEnd();
        int count = (end-start+1);

        ArrayList<AnimationNode> tmp = new ArrayList<AnimationNode>(Arrays.asList(old_anim.getNodeArray()));
        for (int i = start; i <= end; i++) {
            tmp.add(i+count, tmp.get(i).clone());
        }

        AnimationNode[] new_nodes = LogicUtil.toArray(tmp, AnimationNode.class);
        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimation(replacement);

        // Note: if multiple were selected, selects the entire newly created group
        animView.setSelectedIndex(this.animView.getSelectedIndex() + count);
    }

    /**
     * Deletes existing selected animation nodes from the animation.
     * If the animation has only one node, the deletion silently fails. (TODO?)
     */
    public void deleteAnimationNodes() {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null || old_anim.getNodeCount() <= 1) {
            return;
        }

        int start = this.animView.getSelectionStart();
        int end = this.animView.getSelectionEnd();
        int count = (end-start+1);

        ArrayList<AnimationNode> tmp = new ArrayList<AnimationNode>(Arrays.asList(old_anim.getNodeArray()));
        for (int n = 0; n < count && !tmp.isEmpty(); n++) {
            tmp.remove(start);
        }

        AnimationNode[] new_nodes = LogicUtil.toArray(tmp, AnimationNode.class);
        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimation(replacement);

        this.animView.setSelectedItemRange(0); // reset
    }

    /**
     * Updates the value of an existing animation node.
     * Sends a preview update as well
     * 
     * @param nodes Node values to replace it with
     */
    public void updateAnimationNodes(List<AnimationNode> nodes) {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null) {
            return;
        }

        int start = this.animView.getSelectionStart();
        int end = this.animView.getSelectionEnd();

        // System.out.println("NODE[" + index + "] = " + node.serializeToString());

        AnimationNode[] new_nodes = old_anim.getNodeArray().clone();
        for (int i = 0; i < nodes.size(); i++) {
            int new_i = start + i;
            if (new_i >= 0 && new_i <= end && new_i < new_nodes.length) {
                new_nodes[new_i] = nodes.get(i);
            }
        }

        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimationWithoutChange(replacement);

        previewAnimationNode(this.animView.getSelectedIndex(), this.animView.getSelectedNode());
    }

    /**
     * Moves the selected animation nodes up or down based on the offset
     * 
     * @param offset Number of rows to move the node up/down the list
     */
    public void moveAnimationNodes(int offset) {
        Animation old_anim = this.animView.getAnimation();
        if (old_anim == null) {
            return;
        }

        int start = this.animView.getSelectionStart();
        int end = this.animView.getSelectionEnd();
        int count = (end-start+1);

        AnimationNode[] old_nodes = old_anim.getNodeArray();
        ArrayList<AnimationNode> tmp = new ArrayList<AnimationNode>(Arrays.asList(old_nodes));

        // Remove nodes on the old positions
        for (int n = 0; n < count; n++) {
            tmp.remove(start);
        }

        // Insert nodes again at the new positions
        for (int n = 0; n < count; n++) {
            tmp.add(start + offset + n, old_nodes[start + n]);
        }

        AnimationNode[] new_nodes = LogicUtil.toArray(tmp, AnimationNode.class);
        Animation replacement = new Animation(old_anim.getOptions().getName(), new_nodes);
        replacement.setOptions(old_anim.getOptions().clone());
        setAnimation(replacement);

        // Adjust selection also
        this.animView.setSelectedIndex(this.animView.getSelectedIndex() + offset);
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
        return this.animView.getAnimation();
    }

    public Animation loadAnimation() {
        String item = this.animSelectionBox.getSelectedItem();
        return (item == null) ? null : Animation.loadFromConfig(getAnimRootConfig().getNode(item));
    }

    public void setAnimation(Animation animation) {
        setAnimationWithoutChange(animation);

        // Important: let the underlying system know about the updated animation!
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
    }

    public void setAnimationWithoutChange(Animation animation) {
        String old_name = this.animSelectionBox.getSelectedItem();
        String new_name = animation.getOptions().getName();
        boolean is_name_change = (old_name != null && !old_name.equals(new_name));

        // If name was changed, delete the original animation
        if (is_name_change) {
            getAnimRootConfig().remove(old_name);
        }

        // Save to configuration
        animation.saveToParentConfig(getAnimRootConfig());

        if (is_name_change) {
            // If name change, add the new name and select it, then delete the old item
            // This prevents too many unneeded reloading
            this.animSelectionBox.addItem(new_name);
            this.animSelectionBox.setSelectedItem(new_name);
            this.animSelectionBox.removeItem(old_name);
        } else if (this.animSelectionBox.getItems().contains(new_name)) {
            // Refresh animation view only
            this.animView.setAnimation(animation);
        } else {
            // Add the new animation and select it
            this.animSelectionBox.addItem(new_name);
            this.animSelectionBox.setSelectedItem(new_name);
        }
    }

    public ConfigurationNode getAnimRootConfig() {
        return this.attachment.getConfig().getNode("animations");
    }

    public MapWidgetAttachmentNode getAttachment() {
        return this.attachment;
    }

}
