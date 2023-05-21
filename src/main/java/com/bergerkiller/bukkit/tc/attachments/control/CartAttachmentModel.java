package com.bergerkiller.bukkit.tc.attachments.control;

import com.bergerkiller.bukkit.common.map.MapEventPropagation;
import com.bergerkiller.bukkit.common.map.MapTexture;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetTabView;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetText;
import com.bergerkiller.bukkit.common.resources.SoundEffect;
import com.bergerkiller.bukkit.tc.Localization;
import com.bergerkiller.bukkit.tc.TrainCarts;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfig;
import com.bergerkiller.bukkit.tc.attachments.config.SavedAttachmentModel;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetAttachmentNode;
import com.bergerkiller.bukkit.tc.attachments.ui.menus.general.ModelStorageTypeSelectionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.models.MapWidgetModelStoreSelect;
import com.bergerkiller.bukkit.tc.exception.IllegalNameException;
import org.bukkit.entity.Player;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.api.AttachmentType;

public class CartAttachmentModel extends CartAttachment {
    public static final AttachmentType TYPE = new AttachmentType() {
        @Override
        public String getID() {
            return MODEL_TYPE_ID;
        }

        @Override
        public MapTexture getIcon(ConfigurationNode config) {
            return MapTexture.loadPluginResource(TrainCarts.plugin, "com/bergerkiller/bukkit/tc/textures/attachments/model.png");
        }

        @Override
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentModel();
        }

        @Override
        public void createAppearanceTab(MapWidgetTabView.Tab tab, MapWidgetAttachmentNode attachment) {
            final TrainCarts traincarts = TrainCarts.plugin;

            tab.addWidget(new MapWidgetText().setText("Current Model:")).setBounds(0, 3, 100, 16);
            final MapWidgetModelStoreSelect modelSelector = tab.addWidget(new MapWidgetModelStoreSelect(traincarts) {
                @Override
                public void onAttached() {
                    setSelectedModel(getModelOf(traincarts, attachment));
                }

                @Override
                public void onSelectedModelChanged(SavedAttachmentModel model) {
                    attachment.getConfig().set(AttachmentConfig.Model.MODEL_NAME_CONFIG_KEY,
                            model == null ? null : model.getName());
                    sendStatusChange(MapEventPropagation.DOWNSTREAM, "changed", attachment);

                    for (MapWidget widget : tab.getWidgets()) {
                        if (widget instanceof ModelActionButton) {
                            ((ModelActionButton) widget).updateEnabled();
                        }
                    }
                }
            });
            modelSelector.setBounds(0, 13, 100, 13);

            // Load button. Allows players to duplicate an existing model, or load one from paste,
            // or copy an attachment tree elsewhere and load it into it here. These things can
            // also be done using the /train model config set of commands.
            tab.addWidget(new ModelActionButton(traincarts, attachment) {
                @Override
                public void onActivate() {
                    final ModelActionButton selfButton = this;
                    final SavedAttachmentModel model = getModelOf(traincarts, attachment);
                    if (model == null) {
                        setEnabled(false);
                        return;
                    } else if (!checkPerm(model)) {
                        return;
                    }

                    display.playSound(SoundEffect.CLICK);

                    tab.addWidget(new ModelStorageTypeSelectionDialog.LoadDialog() {
                        @Override
                        public void onConfigLoaded(ConfigurationNode attachmentConfig) {
                            if (!checkPerm(model)) {
                                return;
                            }

                            // If model doesn't exist yet, create it. Name might be invalid, check for it.
                            try {
                                traincarts.getSavedAttachmentModels().setConfig(model.getName(), attachmentConfig);
                                modelSelector.setSelectedModel(model); // Ensure if it was missing, it updates
                            } catch (IllegalNameException e) {
                                Localization.COMMAND_MODEL_CONFIG_INVALID_NAME.message(display.getOwners().get(0), model.getName());
                            }
                        }

                        @Override
                        public void close() {
                            super.close();
                            selfButton.focus();
                        }
                    }.setPosition(0, 5));
                }
            }).setText("Load").setBounds(0, 30, 49, 14);

            // Switches the attachment editor to edit the model instead
            tab.addWidget(new ModelActionButton(traincarts, attachment) {
                @Override
                public void onActivate() {
                    final SavedAttachmentModel model = getModelOf(traincarts, attachment);
                    if (model == null) {
                        setEnabled(false);
                    } else if (checkPerm(model)) {
                        boolean isNewModel = model.isNone();

                        // This will probably close the current session / remove this menu
                        Player player = display.getOwners().get(0);
                        traincarts.getPlayer(player).editModel(model);

                        // Inform the player as well
                        if (isNewModel) {
                            Localization.COMMAND_MODEL_CONFIG_EDIT_NEW.message(player, model.getName());
                        } else {
                            Localization.COMMAND_MODEL_CONFIG_EDIT_EXISTING.message(player, model.getName());
                        }
                    }
                }
            }).setText("Edit").setBounds(51, 30, 49, 14);
        }

        private SavedAttachmentModel getModelOf(TrainCarts traincarts, MapWidgetAttachmentNode attachment) {
            String modelName = attachment.getConfig().getOrDefault(AttachmentConfig.Model.MODEL_NAME_CONFIG_KEY, "");
            if (modelName.trim().isEmpty()) {
                return null;
            } else {
                return traincarts.getSavedAttachmentModels().getModelOrNone(modelName);
            }
        }

        class ModelActionButton extends MapWidgetButton {
            private final TrainCarts traincarts;
            private final MapWidgetAttachmentNode attachment;

            public ModelActionButton(TrainCarts traincarts, MapWidgetAttachmentNode attachment) {
                this.traincarts = traincarts;
                this.attachment = attachment;
            }

            public void updateEnabled() {
                setEnabled(getModelOf(traincarts, attachment) != null);
            }

            @Override
            public void onAttached() {
                updateEnabled();
            }

            protected boolean checkPerm(SavedAttachmentModel model) {
                Player editing = display.getOwners().get(0);
                if (model.hasPermission(editing)) {
                    return true;
                } else {
                    Localization.COMMAND_MODEL_CONFIG_CLAIMED.message(editing, model.getName());
                    display.playSound(SoundEffect.EXTINGUISH);
                    return false;
                }
            }
        }
    };

    @Override
    public void makeVisible(Player viewer) {
    }

    @Override
    public void makeHidden(Player viewer) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onMove(boolean absolute) {
    }
}
