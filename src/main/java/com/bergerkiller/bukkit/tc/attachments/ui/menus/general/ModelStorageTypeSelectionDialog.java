package com.bergerkiller.bukkit.tc.attachments.ui.menus.general;

import com.bergerkiller.bukkit.common.Hastebin;
import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetSubmitText;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.TCConfig;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetMenu;
import com.bergerkiller.bukkit.tc.commands.Commands;
import com.bergerkiller.bukkit.tc.controller.global.TrainCartsPlayer;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import com.bergerkiller.bukkit.tc.properties.SavedClaim;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * Simple menu dialog popup that allows the Player to select where
 * to save or load an attachment tree model configuration from/to.
 * Options are clipboard, model store or haste upload/download.
 */
public abstract class ModelStorageTypeSelectionDialog extends MapWidgetMenu {
    private final boolean load;
    private MapWidgetSubmitText textWidget;
    private MapWidgetButton modelStoreButton;
    private Consumer<String> textAccept = t -> {};

    /**
     * Creates a new dialog
     *
     * @param load Whether to (down)load a model (true) or to save/upload a model (false)
     */
    protected ModelStorageTypeSelectionDialog(boolean load) {
        this.load = load;
        this.playSoundWhenBackClosed = true;
        this.setBounds(9, 17, 100, 69);
        this.setBackgroundColor(MapColorPalette.getColor(183, 188, 79));
    }

    protected TrainCarts getTrainCarts() {
        return (TrainCarts) display.getPlugin();
    }

    protected TrainCartsPlayer getPlayerOwner() {
        return getTrainCarts().getPlayer(display.getOwners().get(0));
    }

    protected void askText(String description, Consumer<String> accept) {
        textAccept = accept;
        textWidget.setDescription(description);
        textWidget.activate();
    }

    public abstract void useClipboard();
    public abstract void usePasteServer();
    public abstract void useModelStore();

    @Override
    public void onAttached() {
        super.onAttached();

        // Used to ask for urls / model name
        textWidget = this.addWidget(new MapWidgetSubmitText() {
            @Override
            public void onAccept(String text) {
                // Got to delay by one tick, seems to mess shit up otherwise
                Bukkit.getScheduler().scheduleSyncDelayedTask(display.getPlugin(), () -> textAccept.accept(text));
            }
        });

        // Label
        this.addWidget(new MapWidgetText()
                .setText(load ? "Where to load\nattachments from?"
                              : "Where to save\nattachments to?")
                .setBounds(5, 5, 80, 20));

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                useClipboard();
            }
        }.setText("Clipboard")
         .setBounds(5, 24, 90, 12)
         .setEnabled(!load || getPlayerOwner().getModelClipboard() != null));

        this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                usePasteServer();
            }
        }.setText("Paste Server").setBounds(5, 38, 90, 12));

        modelStoreButton = this.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
                if (Permission.COMMAND_MODEL_CONFIG_LIST.has(display.getOwners().get(0))) {
                    useModelStore();
                } else {
                    setEnabled(false);
                }
            }
        });
        modelStoreButton.setText("Model Store").setBounds(5, 52, 90, 12);
        modelStoreButton.setEnabled(Permission.COMMAND_MODEL_CONFIG_LIST.has(display.getOwners().get(0)));
    }

    public static abstract class LoadDialog extends ModelStorageTypeSelectionDialog {

        protected LoadDialog() {
            super(true);
        }

        /**
         * Called when attachment configuration was selected and loaded
         *
         * @param attachmentConfig Loaded config
         */
        public abstract void onConfigLoaded(ConfigurationNode attachmentConfig);

        @Override
        public void useClipboard() {
            if (getPlayerOwner().getModelClipboard() != null) {
                Localization.ATTACHMENTS_LOAD_CLIPBOARD.message(display.getOwners().get(0));
                display.playSound(SoundEffect.CLICK_WOOD);
                onConfigLoaded(getPlayerOwner().getModelClipboard().clone());
                close();
            } else {
                display.playSound(SoundEffect.EXTINGUISH);
                close();
            }
        }

        @Override
        public void usePasteServer() {
            askText("Enter Paste URL", url -> {
                Commands.importModel(getTrainCarts(), display.getOwners().get(0), url, config -> {
                    Localization.ATTACHMENTS_LOAD_PASTE_SERVER.message(display.getOwners().get(0));
                    display.playSound(SoundEffect.CLICK_WOOD);
                    onConfigLoaded(config);
                    close();
                });
            });
        }

        @Override
        public void useModelStore() {
            askText("Enter Model Name", name -> {
                SavedAttachmentModel model = getTrainCarts().getSavedAttachmentModels().getModel(name);
                if (model != null) {
                    Localization.ATTACHMENTS_LOAD_MODEL_STORE.message(display.getOwners().get(0), name);
                    display.playSound(SoundEffect.CLICK_WOOD);
                    onConfigLoaded(model.getConfig().clone());
                    close();
                } else {
                    Localization.COMMAND_MODEL_CONFIG_NOTFOUND.message(display.getOwners().get(0), name);
                    display.playSound(SoundEffect.EXTINGUISH);
                }
            });
        }
    }

    public static abstract class SaveDialog extends ModelStorageTypeSelectionDialog {
        private final ConfigurationNode attachmentConfig;

        protected SaveDialog(ConfigurationNode attachmentConfig) {
            super(false);
            this.attachmentConfig = attachmentConfig.clone();
        }

        public abstract void onExported();

        @Override
        public void useClipboard() {
            getPlayerOwner().setModelClipboard(attachmentConfig);
            Localization.ATTACHMENTS_SAVE_CLIPBOARD.message(display.getOwners().get(0));
            close();
            onExported();
        }

        @Override
        public void usePasteServer() {
            final Player player = display.getOwners().get(0);

            TCConfig.hastebin.upload(attachmentConfig.toString()).thenAccept(new Consumer<Hastebin.UploadResult>() {
                @Override
                public void accept(Hastebin.UploadResult t) {
                    if (t.success()) {
                        Localization.ATTACHMENTS_SAVE_PASTE_SERVER.message(player, t.url());
                        close();
                        onExported();
                    } else {
                        player.sendMessage(ChatColor.RED + "Failed to export attachment: " + t.error());
                    }
                }
            });
        }

        @Override
        public void useModelStore() {
            final Player player = display.getOwners().get(0);

            askText("Enter Model Name", name -> {
                SavedAttachmentModel model = getTrainCarts().getSavedAttachmentModels().getModelOrNone(name);
                if (model.hasPermission(player)) {
                    try {
                        boolean isNewConfig = model.isNone();
                        getTrainCarts().getSavedAttachmentModels().setConfig(name, attachmentConfig);

                        // Add claim if configured this should happen
                        if (isNewConfig && TCConfig.claimNewSavedModels) {
                            model.setClaims(Collections.singleton(new SavedClaim(player)));
                        }

                        Localization.ATTACHMENTS_SAVE_MODEL_STORE.message(player, name);
                        close();
                        onExported();
                    } catch (IllegalNameException e) {
                        Localization.COMMAND_INPUT_NAME_INVALID.message(player, name);
                    }
                } else {
                    Localization.COMMAND_MODEL_CONFIG_CLAIMED.message(player, name);
                }
            });
        }
    }
}
