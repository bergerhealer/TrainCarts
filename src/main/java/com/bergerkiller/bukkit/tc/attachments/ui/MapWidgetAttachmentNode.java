package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig;
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
    private final AttachmentConfig config;
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

    public List<MapWidgetAttachmentNode> getAttachments() {
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
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "reset");
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
        // Count the total number of elements in the path
        int num_count = 0;
        MapWidgetAttachmentNode tmp = this;
        while (tmp.parentAttachment != null) {
            tmp = tmp.parentAttachment;
            num_count++;
        }

        // Generate path
        tmp = this;
        int[] targetPath = new int[num_count];
        for (int i = targetPath.length-1; i >= 0; i--) {
            targetPath[i] = tmp.parentAttachment.attachments.indexOf(tmp);
            tmp = tmp.parentAttachment;
        }
        return targetPath;
    }

    /**
     * Looks up the attachment that this node refers to. Changes to this attachment will
     * cause live changes.
     * 
     * @return attachment, null if the minecart isn't available or the attachment is missing
     */
    public Attachment getAttachment() {
        AttachmentEditor editor = this.getEditor();
        MinecartMember<?> member;
        if (editor == null || editor.editedCart == null || (member = editor.editedCart.getHolder()) == null) {
            return null; // detached or not loaded
        } else {
            return member.findAttachment(this.getTargetPath());
        }
    }

    public AttachmentEditor getEditor() {
        if (this.display == null && this.root != null) {
            return (AttachmentEditor) this.root.getDisplay();
        } else {
            return (AttachmentEditor) this.getDisplay();
        }
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

        // Only for root nodes: modify Physical properties of the cart
        if (this.parentAttachment == null) {
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
    public void onDraw() {        
        int px = this.col * 17;

        if (this.isFocused()) {
            view.fillRectangle(px, 0, getWidth() - px, getHeight(), MapColorPalette.getColor(220, 220, 220));
        } else if (this.isActivated()) {
            view.fillRectangle(px, 0, getWidth() - px, getHeight(), MapColorPalette.getColor(220, 255, 220));
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
