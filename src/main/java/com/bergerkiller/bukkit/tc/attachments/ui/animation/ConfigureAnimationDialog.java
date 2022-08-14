package com.bergerkiller.bukkit.tc.attachments.ui.animation;

import java.util.function.Consumer;

import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.attachments.animation.Animation;
import com.bergerkiller.bukkit.tc.attachments.animation.AnimationOptions;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetBlinkyButton;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetNumberBox;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.AnimationMenu;

public class ConfigureAnimationDialog extends MapWidgetMenu {
    private final AnimationMenu menu;

    public ConfigureAnimationDialog(AnimationMenu menu) {
        this.setBackgroundColor(MapColorPalette.COLOR_ORANGE);
        this.setBounds(14, 18, 88, 80);
        this.menu = menu;
    }

    @Override
    public void onAttached() {
        super.onAttached();

        // Loop on/off button
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onAttached() {
                super.onAttached();
                updateIcon();
            }

            @Override
            public void onClick() {
                updateOptions(opt -> opt.setLooped(!opt.isLooped()));
                updateIcon();
            }

            private void updateIcon() {
                setIcon(getOptions().isLooped() ? "attachments/anim_config_loop_on.png" : "attachments/anim_config_loop_off.png");
                setTooltip(getOptions().isLooped() ? "Looped: YES" : "Looped: NO");
            }
        }).setClickSound(SoundEffect.CLICK_WOOD).setPosition(11, 7);

        // Autoplay on/off
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onAttached() {
                super.onAttached();
                updateIcon();
            }

            @Override
            public void onClick() {
                updateOptions(opt -> opt.setAutoPlay(!opt.isAutoPlay()));
                updateIcon();
            }

            private void updateIcon() {
                setIcon(getOptions().isAutoPlay() ? "attachments/anim_config_autoplay_on.png" : "attachments/anim_config_autoplay_off.png");
                setTooltip(getOptions().isAutoPlay() ? "Autoplay: YES" : "Autoplay: NO");
            }
        }).setClickSound(SoundEffect.CLICK_WOOD).setPosition(36, 7);

        // Movement controlled on/off
        this.addWidget(new MapWidgetBlinkyButton() {
            @Override
            public void onAttached() {
                super.onAttached();
                updateIcon();
            }

            @Override
            public void onClick() {
                updateOptions(opt -> opt.setMovementControlled(!opt.isMovementControlled()));
                updateIcon();
            }

            private void updateIcon() {
                setIcon(getOptions().isMovementControlled() ? "attachments/anim_config_movecontrol_on.png" : "attachments/anim_config_movecontrol_off.png");
                setTooltip(getOptions().isMovementControlled() ? "Movement-\nControlled: YES" : "Movement-\nControlled: NO");
            }
        }).setClickSound(SoundEffect.CLICK_WOOD).setPosition(61, 7);

        byte lblColor = MapColorPalette.getColor(152, 89, 36);

        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Speed").setPosition(13, 29);
        this.addWidget(new MapWidgetNumberBox() { // Speed
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getOptions().getSpeed());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Animation Speed";
            }

            @Override
            public void onActivate() {
                setValue(1.0);
            }

            @Override
            public void onValueChanged() {
                if (getOptions().getSpeed() != this.getValue()) {
                    updateOptions(opt -> opt.setSpeed(getValue()));
                }
            }
        }).setBounds(4, 38, 80, 11);

        this.addWidget(new MapWidgetText()).setColor(lblColor).setText("Delay").setPosition(13, 54);
        this.addWidget(new MapWidgetNumberBox() { // Delay
            @Override
            public void onAttached() {
                super.onAttached();
                this.setValue(getOptions().getDelay());
            }

            @Override
            public String getAcceptedPropertyName() {
                return "Animation Delay";
            }

            @Override
            public void onValueChanged() {
                if (getOptions().getDelay() != this.getValue()) {
                    updateOptions(opt -> opt.setDelay(getValue()));
                }
            }
        }).setBounds(4, 63, 80, 11);
    }

    private AnimationOptions getOptions() {
        return menu.getAnimation().getOptions();
    }

    private void updateOptions(Consumer<AnimationOptions> func) {
        final Animation anim = menu.getAnimation().clone();
        func.accept(anim.getOptions());
        menu.setAnimation(anim);
        sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed");
        menu.playAnimation(opt -> {
            boolean looped = anim.getOptions().isLooped();
            opt.setSpeed(1.0);
            opt.setLooped(looped);
            opt.setReset(!looped);
            opt.setMovementControlled(anim.getOptions().isMovementControlled());
        });
    }
}
