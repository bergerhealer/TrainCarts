package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.common.utils.WorldUtil;
import com.bergerkiller.bukkit.common.wrappers.BlockData;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentManager;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentBlock;
import com.bergerkiller.bukkit.tc.controller.components.AttachmentControllerMember;
import com.bergerkiller.bukkit.tc.utils.SetCallbackCollector;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.MapPlayerInput.Key;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.common.utils.LogicUtil;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentTypeRegistry;
import com.bergerkiller.bukkit.tc.attachments.control.CartAttachmentItem;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.AnimationMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.AppearanceMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.GeneralMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PhysicalMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;

/**
 * A single attachment node in the attachment node tree, containing
 * the configuration for the node.
 */
public class MapWidgetAttachmentNode extends MapWidget implements ItemDropTarget {
    private final MapWidgetAttachmentTree tree;
    private static MapTexture expanded_icon = null;
    private static MapTexture collapsed_icon = null;
    private AttachmentConfig config;
    private final List<MapWidgetAttachmentNode> attachments = new ArrayList<>();
    private MapWidgetAttachmentNode parentAttachment = null;
    private int col, row;
    private MapTexture icon = null;
    private boolean changingOrder = false;
    private boolean expanded = true;
    private MapWidgetMenuButton appearanceMenuButton;

    public MapWidgetAttachmentNode(MapWidgetAttachmentTree tree, AttachmentConfig config) {
        this.tree = tree;
        this.config = config;
        this.loadFromConfig();

        // Can be focused
        this.setFocusable(true);
    }

    public void loadFromConfig() {
        // Add child attachments
        this.attachments.clear();
        for (AttachmentConfig childConfig : config.children()) {
            MapWidgetAttachmentNode sub = new MapWidgetAttachmentNode(this.tree, childConfig);
            sub.parentAttachment = this;
            this.attachments.add(sub);
        }

        // Special properties
        this.expanded = this.getEditorOption("expanded", true);
        if (!this.expanded && this.attachments.isEmpty()) {
            this.expanded = true;
            this.setEditorOption("expanded", true, true);
        }
    }

    /**
     * Synchronizes this tree of nodes with the configuration specified.
     * If true is returned the view should be refreshed.
     *
     * @param config Root configuration
     * @return True if changes were made, False if perfectly in sync
     */
    public boolean sync(AttachmentConfig config) {
        this.config = config;
        this.resetIcon(); // Just in case
        if (this.isActivated() && appearanceMenuButton != null) {
            appearanceMenuButton.setIcon(getIcon());
        }

        boolean changed = false;
        List<AttachmentConfig> childConfigs = config.children();
        for (int i = 0; i < childConfigs.size(); i++) {
            AttachmentConfig childConfig = childConfigs.get(i);
            if (i < attachments.size()) {
                MapWidgetAttachmentNode node = attachments.get(i);
                if (node.getConfig() == childConfig.config()) {
                    changed |= node.sync(childConfig);
                    continue;
                }

                // Try to find an attachment child further up, and move it to the new position
                // If found, view must be re-calculated
                boolean found = false;
                for (int j = i + 1; j < attachments.size(); j++) {
                    node = attachments.get(j);
                    if (node.getConfig() == childConfig.config()) {
                        attachments.remove(j);
                        attachments.add(i, node);
                        changed = true;
                        found = true;
                        node.sync(childConfig);
                        break;
                    }
                }
                if (found) {
                    continue;
                }
            }

            // Insert a new attachment at this position
            MapWidgetAttachmentNode node = new MapWidgetAttachmentNode(this.tree, childConfig);
            node.parentAttachment = this;
            this.attachments.add(i, node);
            changed = true;
        }

        // Remove all excess attachments
        while (attachments.size() > childConfigs.size()) {
            attachments.remove(childConfigs.size());
            changed = true;
        }

        return changed;
    }

    public MapWidgetAttachmentTree getTree() {
        return this.tree;
    }

    public MapWidgetAttachmentNode getParentAttachment() {
        return this.parentAttachment;
    }

    public void setParentAttachment(MapWidgetAttachmentNode newParent) {
        this.parentAttachment = newParent;
    }

    public void openMenu(MenuItem item) {
        // Open the menu
        getTree().onMenuOpen(this, item);
    }

    public List<MapWidgetAttachmentNode> getChildAttachmentNodes() {
        return this.attachments;
    }

    /**
     * Gets the configuration of this node.
     * 
     * @return node configuration
     */
    public ConfigurationNode getConfig() {
        return this.config.config();
    }

    /**
     * Gets the {@link AttachmentConfig} of just this node.
     *
     * @return attachment config
     */
    public AttachmentConfig getAttachmentConfig() {
        return this.config;
    }

    /**
     * Gets an option for this node under the 'editor' block
     * 
     * @param name of the option
     * @param defaultValue to return if not set
     * @return option
     */
    public <T> T getEditorOption(String name, T defaultValue) {
        ConfigurationNode config = this.getConfig();
        if (config.contains("editor." + name)) {
            return config.get("editor." + name, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Sets an option for this node under the 'editor' block. If the value
     * is the default value, the option is removed. If the editor block has
     * no more options, it is removed also.
     * 
     * @param name Name of the option
     * @param defaultValue Default value. If current value is default, the
     *                     option is not written to the configuration
     * @param value Value of the option
     */
    public <T> void setEditorOption(String name, T defaultValue, T value) {
        ConfigurationNode config = this.getConfig();

        // Don't create an editor block unless absolutely needed
        if (!config.contains("editor." + name)) {
            if (LogicUtil.bothNullOrEqual(defaultValue, value)) {
                return;
            }
        }

        // Update it
        config.set("editor." + name, value);
    }

    /**
     * Applies updated configurations to the model system, refreshing trains that use this model
     */
    public void update() {
        this.getTree().sync();
    }

    public MapWidgetAttachmentNode addAttachment(ConfigurationNode config) {
        return addAttachment(this.attachments.size(), config);
    }

    public MapWidgetAttachmentNode addAttachment(int index, ConfigurationNode config) {
        MapWidgetAttachmentNode attachment = new MapWidgetAttachmentNode(this.tree,
                this.config.addChild(index, config));
        attachment.parentAttachment = this;
        this.attachments.add(index, attachment);
        return attachment;
    }

    public void remove() {
        if (this.parentAttachment != null && this.parentAttachment.attachments.remove(this)) {
            this.config.remove();
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "reset");
        }
    }

    /**
     * Sets the column and row this node is displayed at.
     * This controls the indent level when drawing.
     * 
     * @param col Cell column
     * @param row Cell row
     */
    public void setCell(int col, int row) {
        this.col = col;
        this.row = row;
    }

    /**
     * Gets the column of where this node is displayed
     * 
     * @return cell column
     */
    public int getCellColumn() {
        return this.col;
    }

    /**
     * Gets the row of where this node is displayed
     * 
     * @return cell row
     */
    public int getCellRow() {
        return this.row;
    }

    public AttachmentType getType() {
        return AttachmentTypeRegistry.instance().fromConfig(this.getConfig());
    }

    public void setType(AttachmentType type) {
        AttachmentTypeRegistry.instance().toConfig(this.getConfig(), type);
    }

    /**
     * Computes the iteration of attachment indices required to get to this attachment.
     * This path is suitable for {@link MinecartMember#playAnimationFor}.
     * 
     * @return target path
     */
    public int[] getTargetPath() {
        return config.childPath();
    }

    /**
     * Looks up the attachment that this node refers to. Changes to this attachment will
     * cause live changes.
     * 
     * @return attachment, null if the minecart isn't available or the attachment is missing
     * @deprecated There technically can be more than one. Use {@link #getAttachments()} instead.
     * @see #getAttachments()
     */
    @Deprecated
    public Attachment getAttachment() {
        List<Attachment> attachments = this.config.liveAttachments();
        return attachments.isEmpty() ? null : attachments.get(0);
    }

    /**
     * Looks up a List of all the attachment instances that use this attachment configuration.
     * Changes to these attachments will cause live changes.
     *
     * @return List of live attachments using this attachment configuration
     */
    public List<Attachment> getAttachments() {
        return config.liveAttachments();
    }

    /**
     * Gets a set of live MinecartMember instances of carts using this particular
     * attachment. When editing a model attachment, more than one member might be returned
     * if more than one member is using it in a MODEL attachment.
     *
     * @return members using this attachment
     */
    public Set<MinecartMember<?>> getMembersUsingAttachment() {
        SetCallbackCollector<MinecartMember<?>> collector = new SetCallbackCollector<>();
        config.runAction(attachment -> {
            AttachmentManager manager = attachment.getManager();
            if (manager instanceof AttachmentControllerMember) {
                MinecartMember<?> member = ((AttachmentControllerMember) manager).getMember();
                if (!member.isUnloaded()) {
                    collector.accept(member);
                }
            }
        });
        return collector.result();
    }

    public AttachmentEditor getEditor() {
        if (this.display == null && this.root != null) {
            return (AttachmentEditor) this.root.getDisplay();
        } else {
            return (AttachmentEditor) this.getDisplay();
        }
    }

    public boolean checkModifyPermissions() {
        if (display != null) {
            AttachmentType type = getType();
            for (Player player : display.getOwners()) {
                if (!type.hasPermission(player)) {
                    for (Player notifPlayer : display.getOwners()) {
                        notifPlayer.sendMessage(ChatColor.RED +
                                "You do not have permission to modify this type of attachment");
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void onAttached() {
        this.setSize(this.parent.getWidth(), 18);
    }

    @Override
    public void onActivate() {
        super.onActivate();

        // Sometimes activates without being attached? Weird.
        if (this.display == null) {
            return;
        }

        // Play a neat sliding sound
        display.playSound(SoundEffect.PISTON_EXTEND);

        // After being activated, add a bunch of buttons that can be pressed
        // Each button will open its own context menu to edit things
        // The buttons shown here depend on the type of the node, somewhat
        int px = this.col * 17 + 1;
        this.appearanceMenuButton = this.addWidget(new MapWidgetMenuButton(MenuItem.APPEARANCE));
        this.appearanceMenuButton.setTooltip("Appearance").setIcon(getIcon()).setPosition(px, 1);
        px += 17;

        // Only for root nodes when editing carts: modify Physical properties of the cart
        if (this.parentAttachment == null && getEditor().getEditedCartProperties() != null) {
            this.addWidget(new MapWidgetMenuButton(MenuItem.PHYSICAL).setIcon("attachments/physical.png").setPosition(px, 1));
            px += 17;
        }

        // Change 3D position of the attachment
        this.addWidget(new MapWidgetMenuButton(MenuItem.POSITION).setIcon("attachments/move.png").setPosition(px, 1));
        px += 17;

        // Animation frames for an attachment
        this.addWidget(new MapWidgetMenuButton(MenuItem.ANIMATION).setIcon("attachments/animation.png").setPosition(px, 1));
        px += 17;

        // Drops down a menu to add/remove/move the attachment entry
        this.addWidget(new MapWidgetMenuButton(MenuItem.GENERAL).setIcon("attachments/general_menu.png").setPosition(px, 1));
        px += 17;

        // Enabled/disabled
        if (this.isChangingOrder()) {
            for (MapWidget child : this.getWidgets()) {
                child.setEnabled(false);
            }
        }
    }

    @Override
    public void onDeactivate() {
        this.clearWidgets();

        // Play a neat sliding sound
        display.playSound(SoundEffect.PISTON_CONTRACT);
    }

    @Override
    public void onFocus() {
        // Click navigation sounds
        //display.playSound(CommonSounds.CLICK_WOOD);
        this.activate();
    }

    @Override
    public void onKeyPressed(MapKeyEvent event) {
        if (event.getKey() == Key.LEFT &&
            this.parentAttachment != null &&
            this.getWidgetCount() > 0 &&
            this.getWidget(0).isFocused() &&
            !this.attachments.isEmpty())
        {
            this.setExpanded(!this.isExpanded());
        } else {
            super.onKeyPressed(event);
        }
    }

    @Override
    public boolean acceptItem(ItemStack item) {
        // If this is an item attachment, set the item
        // TODO: Make this generic and part of the Type API
        if (this.getType() == CartAttachmentItem.TYPE) {
            this.getConfig().set("item", item.clone());
            sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");

            // Redraw the appearance icon
            this.resetIcon();
            ((MapWidgetMenuButton) this.getWidget(0)).setIcon(getIcon());
            return true;
        }
        return false;
    }

    @Override
    public void onBlockInteract(PlayerInteractEvent event) {
        // If this is a block attachment, set the block
        // TODO: Make this generic and part of the Type API
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
            this.getType() == CartAttachmentBlock.TYPE
        ) {
            Block block = event.getClickedBlock();
            if (block != null) {
                BlockData blockData = WorldUtil.getBlockData(block);
                this.getConfig().set("blockData", blockData.serializeToString());
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");

                // Redraw the appearance icon
                this.resetIcon();
                ((MapWidgetMenuButton) this.getWidget(0)).setIcon(getIcon());
                event.setUseInteractedBlock(Event.Result.DENY);
            }
        }
    }

    @Override
    public void onDraw() {        
        int px = this.col * 17;

        if (this.isActivated() || this.isFocused()) {
            byte bgColor;
            if (getEditor().isEditingSavedModel()) {
                // More appropriate style color when editing a saved model
                bgColor = MapColorPalette.getColor(77, 238, 250);
            } else {
                bgColor = MapColorPalette.getColor(220, 255, 220);
            }
            view.fillRectangle(px, 0, getWidth() - px, getHeight(), bgColor);
        }

        // Draw dots to the left to show our tree hierarchy
        // When we are root, we do something special (?)
        if (this.parentAttachment == null) {
            // Do something special
            // No node for now.
        } else {
            // There is an odd/even problem with the dot pattern
            // Find out our index as a child to correct this pattern problem
            int dotOffset = (((this.row - this.parentAttachment.row) & 0x1) == 0x1) ? 1 : 0;

            // Dot pattern vertical line to top
            byte dotColor = MapColorPalette.getColor(64, 64, 64);
            for (int n = 0; n < 5; n++) {
                this.view.drawPixel(px - 17 + 8, n * 2 + dotOffset, dotColor);
            }

            // Dot pattern horizontal
            for (int n = 1; n < 5; n++) {
                this.view.drawPixel(px - 17 + 8 + n * 2, 8 + dotOffset, dotColor);
            }

            // If not last child, continue dot pattern down
            int childIdx = this.parentAttachment.attachments.indexOf(this);
            if (childIdx != (this.parentAttachment.attachments.size() - 1)) {
                for (int n = 5; n < 9; n++) {
                    this.view.drawPixel(px - 17 + 8, n * 2 + dotOffset, dotColor);
                }
            }

            // For all further parent levels down, check if there is another child
            // If there is, draw a vertical dotted line to indicate so
            int tmpX = px - 26;
            MapWidgetAttachmentNode tmpNode = this.parentAttachment;
            while (tmpNode != null) {
                MapWidgetAttachmentNode tmpNodeParent = tmpNode.parentAttachment;
                if (tmpNodeParent != null && tmpNode != tmpNodeParent.attachments.get(tmpNodeParent.attachments.size() - 1)) {
                    // Node has a parent and is not the last child of that parent: we need to draw a line
                    // Use the known row property to calculate the dot index
                    int childDotOffset = (((this.row - tmpNodeParent.row) & 0x1) == 0x1) ? 1 : 0;
                    for (int n = 0; n < 9; n++) {
                        this.view.drawPixel(tmpX, n * 2 + childDotOffset, dotColor);
                    }
                }
                tmpNode = tmpNodeParent;
                tmpX -= 17;
            }

            // When this node has children, show a [+] or [-] depending on collapsed or not
            // Do not show for the parent node!
            if (!this.attachments.isEmpty()) {
                if (this.expanded) {
                    if (expanded_icon == null) {
                        expanded_icon = this.getDisplay().loadTexture("com/bergerkiller/bukkit/tc/textures/attachments/expanded.png");
                    }
                    view.draw(expanded_icon,  px-9-(expanded_icon.getWidth()/2), (view.getHeight()-expanded_icon.getHeight())/2 + dotOffset);
                } else {
                    if (collapsed_icon == null) {
                        collapsed_icon = this.getDisplay().loadTexture("com/bergerkiller/bukkit/tc/textures/attachments/collapsed.png");
                    }
                    view.draw(collapsed_icon, px-9-(collapsed_icon.getWidth()/2), (view.getHeight()-collapsed_icon.getHeight())/2 + dotOffset);
                }
            }
        }

        // Draw icon and maybe labels or other stuff when not activated
        if (!this.isActivated()) {
            view.draw(getIcon(), px + 1, 1);
        }

        // Show different focus rectangles depending on the mode
        if (this.isChangingOrder()) {
            view.drawRectangle(px, 0, getWidth() - px, getHeight(), MapColorPalette.COLOR_RED);
        } else if (this.isFocused()) {
            view.drawRectangle(px, 0, getWidth() - px, getHeight(), MapColorPalette.COLOR_BLACK);
        } else if (this.isActivated()) {
            view.drawRectangle(px, 0, getWidth() - px, getHeight(), MapColorPalette.COLOR_GREEN);
        }
    }

    public void resetIcon() {
        this.icon = null;
    }

    public void setChangingOrder(boolean changing) {
        if (this.changingOrder != changing) {
            this.changingOrder = changing;
            this.invalidate();
            for (MapWidget child : this.getWidgets()) {
                child.setEnabled(!changing);
            }
        }
    }

    public boolean isChangingOrder() {
        return this.changingOrder;
    }

    public void setExpanded(boolean expanded) {
        if (this.expanded != expanded) {
            this.expanded = expanded;
            this.setEditorOption("expanded", true, this.expanded);
            this.getTree().updateView();
            this.invalidate();
        }
    }

    public boolean isExpanded() {
        return this.expanded;
    }

    private MapTexture getIcon() {
        if (this.icon == null) {
            AttachmentType type = this.getType();
            if (type == null) {
                this.icon = MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/missing.png");
            } else {
                this.icon = this.getType().getIcon(this.getConfig());
            }
        }
        return this.icon;
    }

    @Override
    public String toString() {
        AttachmentType type = this.getType();
        String name = (type == null) ? "MISSING_TYPE" : type.toString();
        for (int p : this.getTargetPath()) {
            name += "." + p;
        }
        return name;
    }

    private class MapWidgetMenuButton extends MapWidgetBlinkyButton {
        private final MenuItem _menu;

        public MapWidgetMenuButton(MenuItem menu) {
            this._menu = menu;
            this.setTooltip(Character.toUpperCase(menu.name().charAt(0)) + menu.name().substring(1).toLowerCase(Locale.ENGLISH));
        }

        @Override
        public void onClick() {
            openMenu(this._menu);
        }
    }

    public enum MenuItem {
        APPEARANCE(AppearanceMenu::new),
        POSITION(PositionMenu::new),
        ANIMATION(AnimationMenu::new),
        GENERAL(GeneralMenu::new),
        PHYSICAL(PhysicalMenu::new);

        private final Supplier<? extends MapWidgetMenu> _menuConstructor;

        MenuItem(Supplier<? extends MapWidgetMenu> menuConstructor) {
            this._menuConstructor = menuConstructor;
        }

        public MapWidgetMenu createMenu(MapWidgetAttachmentNode attachmentNode) {
            MapWidgetMenu menu = this._menuConstructor.get();
            menu.setAttachment(attachmentNode);
            return menu;
        }
    }

}
