package com.bergerkiller.bukkit.tc.attachments.ui;

import org.bukkit.entity.EntityType;

import com.bergerkiller.bukkit.common.events.map.MapStatusEvent;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.map.MapSessionMode;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetWindow;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode.MenuItem;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.AppearanceMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.PositionMenu;
import com.bergerkiller.bukkit.tc.properties.CartProperties;

public class AttachmentEditor extends MapDisplay {
    public AttachmentModel model;

    private MapWidgetAttachmentTree tree = new MapWidgetAttachmentTree() {
        @Override
        public void onMenuOpen(MapWidgetAttachmentNode node, MenuItem menu) {
            if (menu == MenuItem.APPEARANCE) {
                AttachmentEditor.this.addWidget(new AppearanceMenu(node));
            } else if (menu == MenuItem.POSITION) {
                AttachmentEditor.this.addWidget(new PositionMenu(node));
            }
        }
    };

    @Override
    public void onTick() {
        //this.getWidgets().get(new Random().nextInt(this.getWidgets().size())).focus();
        CartProperties.getEditing(this.getViewers().get(0));
    }

    @Override
    public void onStatusChanged(MapStatusEvent event) {
        if (event.isName("changed")) {
            this.tree.updateModel();
        }
    }

    @Override
    public void onAttached() {
        this.setGlobal(false);
        this.setUpdateWithoutViewers(false);
        this.setSessionMode(MapSessionMode.HOLDING);

        /*
        CartProperties properties = CartProperties.getEditing(this.getViewers().get(0));
        if (properties == null) {
            this.member = null;
            this.setRunning(false);
            return;
        } else {
            this.member = properties.getHolder();
        }
        */

        //getLayer().draw(this.loadTexture("com/bergerkiller/bukkit/tc/textures/attachments/background.png"), 0, 0);
        //getLayer(1).draw(MapFont.MINECRAFT, 5, 5, MapColorPalette.COLOR_BLACK, "Minecart attachments");

        MapWidgetWindow window = new MapWidgetWindow();
        window.setBounds(0, 0, getWidth(), getHeight());
        window.getTitle().setText("Attachment Editor");

        CartProperties prop = CartProperties.getEditing(this.getOwners().get(0));
        if (prop != null) {
            this.model = prop.getModel();
        } else {
            this.model = AttachmentModel.getDefaultModel(EntityType.MINECART);
        }

        this.tree.setModel(this.model);
        window.addWidget(this.tree.setBounds(5, 13, 7 * 17, 6 * 17));
        
        
        
        /*
        window.addWidget(new MapWidgetButton().setBounds(10, 30, 32, 32));
        window.addWidget(new MapWidgetButton().setBounds(50, 30, 32, 32));
        window.addWidget(new MapWidgetButton().setBounds(90, 30, 32, 32));
        window.addWidget(new MapWidgetButton().setBounds(10, 70, 32, 32));
        window.addWidget(new MapWidgetButton().setBounds(50, 70, 32, 32));
        window.addWidget(new MapWidgetButton().setBounds(90, 70, 32, 32));
        
        window.addWidget(new MapWidgetButton().setBounds(50, 10, 32, 16));
        window.addWidget(new MapWidgetButton().setBounds(50, 108, 32, 16));
        
        window.addWidget(new MapWidgetButton().setBounds(0, 50, 8, 8));
        window.addWidget(new MapWidgetButton().setBounds(122, 50, 8, 8));
        */
        
        this.addWidget(window);
        
        this.setReceiveInputWhenHolding(true);
        
        /*
        this.addWidget(new MapWidget() {
            int dx = 1;
            int dy = 1;

            @Override
            public void onAttached() {
                this.setBounds(10, 32, 32, 32);

                this.addWidget(new MapWidget() {
                    @Override
                    public void onDraw() {
                        this.view.fillItem(MapResourcePack.SERVER, new ItemStack(Material.DIAMOND));
                    }
                }.setBounds(8, 8, 16, 16));
            }

            @Override
            public void onTick() {
                this.setPosition(this.getX() + dx, this.getY() + dy);
                if (this.getX() == 90 || this.getX() == 10) {
                    this.dx = -this.dx;
                }
                if (this.getY() == 90 || this.getY() == 32) {
                    this.dy = -this.dy;
                }
            }

            @Override
            public void onDraw() {
                view.fillItem(MapResourcePack.SERVER, new ItemStack(Material.WORKBENCH));
            }
        });
        */
    }

}
