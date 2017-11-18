package com.bergerkiller.bukkit.tc.attachments.control;

import org.bukkit.entity.Player;

public class CartAttachmentModel extends CartAttachment {

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

    @Override
    protected void onLoad() {
        // Add the model declared as a child of this attachment
        String modelName = this.config.get("model", String.class);
        if (modelName != null) {
            // Look up the model name in the model store
            
        }

        // This will initialize the attachments, including the one we added as a child node
        super.onLoad();
    }
}
