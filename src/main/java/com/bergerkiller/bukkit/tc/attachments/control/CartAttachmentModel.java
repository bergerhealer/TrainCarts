package com.bergerkiller.bukkit.tc.attachments.control;

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
        public Attachment createController(ConfigurationNode config) {
            return new CartAttachmentModel();
        }
    };

    @Override
    public void makeVisible(Player viewer) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void makeHidden(Player viewer) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onTick() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void onMove(boolean absolute) {
        // TODO Auto-generated method stub
        
    }

}
