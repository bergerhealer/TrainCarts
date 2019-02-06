package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.AnimationMenu;

public class ConfigureAnimationDialog extends MapWidgetMenu {
    private final AnimationMenu menu;

    public ConfigureAnimationDialog(AnimationMenu menu) {
        this.setBackgroundColor(MapColorPalette.COLOR_ORANGE);
        this.setBounds(5, 15, 105, 88);
        this.menu = menu;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        byte lblColor = MapColorPalette.getColor(152, 89, 36);

        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Speed").setPosition(20, 8);
        this.addWidget(new MapWidgetNumberBox() { // Speed
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(menu.getAnimation().getOptions().getSpeed());
            }

            @Override
            public void onActivate() {
                setValue(1.0);
            }

            @Override
            public void onValueChanged() {
                Animation anim = menu.getAnimation();
                if (anim.getOptions().getSpeed() != this.getValue()) {
                    anim = anim.clone();
                    anim.getOptions().setSpeed(this.getValue());
                    menu.setAnimation(anim);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    menu.playAnimation(false, anim.getOptions().isLooped());
                }
            }
        }).setBounds(10, 17, 80, 11);

        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Delay").setPosition(20, 33);
        this.addWidget(new MapWidgetNumberBox() { // Delay
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(menu.getAnimation().getOptions().getDelay());
            }

            @Override
            public void onValueChanged() {
                Animation anim = menu.getAnimation();
                if (anim.getOptions().getDelay() != this.getValue()) {
                    anim = anim.clone();
                    anim.getOptions().setDelay(this.getValue());
                    menu.setAnimation(anim);
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                    menu.playAnimation(false, anim.getOptions().isLooped());
                }
            }
        }).setBounds(10, 42, 80, 11);

        this.addWidget(new MapWidgetButton() { // Looped
            @Override
            public void onAttached() {
                super.onAttached();
                this.updateText();
            }

            @Override
            public void onActivate() {
                Animation anim = menu.getAnimation();
                anim = anim.clone();
                anim.getOptions().setLooped(!anim.getOptions().isLooped());
                menu.setAnimation(anim);
                sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
                updateText();
                menu.playAnimation(false, anim.getOptions().isLooped());
            }

            private void updateText() {
                this.setText("Looped: " +
                    (menu.getAnimation().getOptions().isLooped() ? "YES" : "NO"));
            }
        }).setBounds(15, 65, 75, 13);
    }
}
