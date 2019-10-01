package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.bergerkiller.bukkit.tc.attachments.api.Attachment;

/**
 * Instance of a glow animation that, every tick, glows an attachment shortly
 * moving through the different children.
 */
public class AttachmentGlowAnimation {
    private List<Attachment> attachments = Collections.emptyList();
    private List<AttachmentGlowAnimation> running = new ArrayList<AttachmentGlowAnimation>();
    private int ctr = 0;

    public AttachmentGlowAnimation() {
    }

    private AttachmentGlowAnimation(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public void start(Attachment start) {
        if (start != null && !start.getChildren().isEmpty()) {
            this.running.add(new AttachmentGlowAnimation(start.getChildren()));
        }
    }

    public boolean next() {
        // Animate all the attachments in our list, in order
        if (ctr > 0 && (ctr-1) < this.attachments.size()) {
            this.attachments.get(ctr-1).setFocused(false);
            start(this.attachments.get(ctr-1));
        }
        if (ctr >= 0 && ctr < this.attachments.size()) {
            this.attachments.get(ctr).setFocused(true);
        }
        ctr++;

        // Process all running child animations in parallel
        Iterator<AttachmentGlowAnimation> iter = this.running.iterator();
        while (iter.hasNext()) {
            if (!iter.next().next()) {
                iter.remove();
            }
        }
        return (ctr <= this.attachments.size()) || !this.running.isEmpty();
    }
}
