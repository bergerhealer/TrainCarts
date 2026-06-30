package com.bergerkiller.bukkit.tc.commands;

import com.bergerkiller.bukkit.common.inventory.CommonItemStack;
import com.bergerkiller.bukkit.common.map.MapDisplay;
import com.bergerkiller.bukkit.common.wrappers.ChatText;
import com.bergerkiller.bukkit.tc.Permission;
import com.bergerkiller.bukkit.tc.attachments.api.Attachment;
import com.bergerkiller.bukkit.tc.attachments.ui.AttachmentEditor;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandRequiresPermission;
import com.bergerkiller.bukkit.tc.commands.annotations.CommandTargetTrain;
import com.bergerkiller.bukkit.tc.commands.argument.AttachmentsByName;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.bukkit.data.ProtoItemStack;

public class AttachmentCommands {
    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("train attachments")
    @CommandDescription("Gives an attachment editor map item to the player")
    private void commandGiveAttachmentEditor(
            final Player sender
    ) {
        CommonItemStack item = CommonItemStack.of(MapDisplay.createMapItem(AttachmentEditor.class))
                .setCustomNameMessage("Traincarts Attachments Editor")
                .setFilledMapColor(0xFF0000);
        sender.getInventory().addItem(item.toBukkit());
        sender.sendMessage(ChatColor.GREEN + "Given a Traincarts attachments editor");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("train attachments update item <attachment_name> <item> ")
    @CommandDescription("Updates the displayed item of an item attachment")
    private void trainCommandSetAttachmentItem(
            final CommandSender sender,
            final @Argument(value="attachment_name", parserName="trainItemDisplayAttachments") AttachmentsByName<Attachment.ItemDisplayAttachment> itemAttachments,
            final @Argument("item") ProtoItemStack protoItem
    ) {
        itemAttachments.validate();

        final ItemStack item = protoItem.createItemStack(1);
        itemAttachments.attachments().forEach(a -> {
            a.setDisplayedItem(item);
        });
        sender.sendMessage(ChatColor.YELLOW + "Item updated for " + itemAttachments.attachments().size() + " attachment(s)");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("cart attachments update item <attachment_name> <item> ")
    @CommandDescription("Updates the displayed item of an item attachment")
    private void cartCommandSetAttachmentItem(
            final CommandSender sender,
            final @Argument(value="attachment_name", parserName="cartItemDisplayAttachments") AttachmentsByName<Attachment.ItemDisplayAttachment> itemAttachments,
            final @Argument("item") ProtoItemStack protoItem
    ) {
        itemAttachments.validate();

        final ItemStack item = protoItem.createItemStack(1);
        itemAttachments.attachments().forEach(a -> {
            a.setDisplayedItem(item);
        });
        sender.sendMessage(ChatColor.YELLOW + "Item updated for " + itemAttachments.attachments().size() + " attachment(s)");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("train attachments update text <attachment_name> <text> ")
    @CommandDescription("Updates the displayed item of an item attachment")
    private void trainCommandSetAttachmentText(
            final CommandSender sender,
            final @Argument(value="attachment_name", parserName="trainTextDisplayAttachments") AttachmentsByName<Attachment.TextDisplayAttachment> textAttachments,
            final @Quoted @Argument("text") String text
    ) {
        textAttachments.validate();

        final ChatText chatText = ChatText.fromMessage(text);
        textAttachments.attachments().forEach(a -> {
            a.setDisplayedText(chatText);
        });
        sender.sendMessage(ChatColor.YELLOW + "Text updated for " + textAttachments.attachments().size() + " attachment(s)");
    }

    @CommandTargetTrain
    @CommandRequiresPermission(Permission.COMMAND_GIVE_EDITOR)
    @Command("cart attachments update text <attachment_name> <text> ")
    @CommandDescription("Updates the displayed item of an item attachment")
    private void cartCommandSetAttachmentText(
            final CommandSender sender,
            final @Argument(value="attachment_name", parserName="cartTextDisplayAttachments") AttachmentsByName<Attachment.TextDisplayAttachment> textAttachments,
            final @Quoted @Argument("text") String text
    ) {
        textAttachments.validate();

        final ChatText chatText = ChatText.fromMessage(text);
        textAttachments.attachments().forEach(a -> {
            a.setDisplayedText(chatText);
        });
        sender.sendMessage(ChatColor.YELLOW + "Text updated for " + textAttachments.attachments().size() + " attachment(s)");
    }
}
