package com.bergerkiller.bukkit.tc;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.tc.attachments.config.AttachmentConfigTracker;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verifies the various ways in which an attachment tree configuration can
 * be modified (by the editor) and that all of them fire the correct events.
 */
public class AttachmentConfigTrackerTest {

    @Test
    public void testLegacyYamlLogicIsListening() {
        // Not visible by default so we got to do some weird shit
        try {
            // Prepare the reflection crap
            Object logic = Class.forName("com.bergerkiller.bukkit.tc.attachments.config.YamlLogic$YamlLogicLegacy")
                    .getConstructor().newInstance();
            java.lang.reflect.Method isListening = logic.getClass().getMethod("isListening",
                    ConfigurationNode.class, AttachmentConfigTracker.class);

            // Create a new tracker and start/stop listening
            // The isListening should reflect whether or not its still listening
            ConfigurationNode root = createAttachment("ITEM");
            TestTracker tracker = TestTracker.track(root);
            assertTrue((Boolean) isListening.invoke(logic, root, tracker));
            tracker.stop();
            assertFalse((Boolean) isListening.invoke(logic, root, tracker));
            tracker.start();
            assertTrue((Boolean) isListening.invoke(logic, root, tracker));
        } catch (Throwable t) {
            throw new IllegalStateException("Failed", t);
        }
    }

    @Test
    public void testModifyThenRemove() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.set("position.y", 200.0);
        mid.remove();

        tracker.sync();
        tracker.assertRemoved(0);
        tracker.assertNone();
    }

    @Test
    public void testClearChildren() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        root.getNodeList("attachments").clear();

        tracker.sync();
        tracker.assertRemoved(0);
        tracker.assertRemoved(0); // TEXT moved from [1] to [0]
        tracker.assertNone();
    }

    @Test
    public void testCloneAddAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        mid.getNodeList("attachments").add(item.clone());

        tracker.sync();
        tracker.assertAdded(0, 2);
        tracker.assertNone();
    }

    @Test
    public void testMoveAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.remove();
        mid.getNodeList("attachments").add(0, item);

        tracker.sync();
        tracker.assertRemoved(0, 1);
        tracker.assertAdded(0, 0);
        tracker.assertNone();
    }

    @Test
    public void testRemoveAndInsertAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.remove();
        mid.getNodeList("attachments").add(0, createAttachment("NEAT"));

        tracker.sync();
        tracker.assertRemoved(0, 1);
        tracker.assertAdded(0, 0);
        tracker.assertNone();
    }

    @Test
    public void testRemoveAndAddAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.remove();
        addAttachment(mid, "NEAT");

        tracker.sync();
        tracker.assertRemoved(0, 1);
        tracker.assertAdded(0, 1);
        tracker.assertNone();
    }

    @Test
    public void testChangeMultipleAttachments() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        root.set("position.x", 1.0);
        root.set("position.y", 2.0);
        root.set("position.z", 3.0);

        item.set("position.x", 1.0);
        item.set("position.y", 2.0);
        item.set("position.z", 3.0);

        tracker.sync();
        tracker.assertChanged();
        tracker.assertChanged(0, 1);
        tracker.assertNone();
    }

    @Test
    public void testChangeSingleAttachmentChangeType() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.set("type", "NOT_AN_ITEM");

        tracker.sync();
        tracker.assertRemoved(0, 1);
        tracker.assertAdded(0, 1);
        tracker.assertNone();
    }

    @Test
    public void testChangeSingleAttachmentAddField() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.set("newfield", "value");

        tracker.sync();
        tracker.assertChanged(0, 1);
        tracker.assertNone();
    }

    @Test
    public void testChangeSingleAttachment() {
        ConfigurationNode root = createAttachment("ENTITY");
        ConfigurationNode mid = addAttachment(root, "EMPTY");
        ConfigurationNode seat = addAttachment(mid, "SEAT");
        ConfigurationNode item = addAttachment(mid, "ITEM");
        ConfigurationNode text = addAttachment(root, "TEXT");

        TestTracker tracker = TestTracker.track(root);

        item.set("position.x", 1.0);
        item.set("position.y", 2.0);
        item.set("position.z", 3.0);

        tracker.sync();
        tracker.assertChanged(0, 1);
        tracker.assertNone();
    }

    private static ConfigurationNode addAttachment(ConfigurationNode parent, String type) {
        ConfigurationNode attachment = createAttachment(type);
        parent.getNodeList("attachments").add(attachment);
        return attachment;
    }

    private static ConfigurationNode createAttachment(String type) {
        ConfigurationNode node = new ConfigurationNode();
        node.set("type", type);
        ConfigurationNode position = node.getNode("position");
        position.set("x", 0.0);
        position.set("y", 0.0);
        position.set("z", 0.0);
        return node;
    }

    /**
     * Implements the tracker so the reported changes can be asserted against
     */
    private static class TestTracker extends AttachmentConfigTracker {
        private final List<ChangeResult> changes = new ArrayList<>();

        public TestTracker(ConfigurationNode config) {
            super(config);
        }

        public static TestTracker track(ConfigurationNode config) {
            TestTracker t = new TestTracker(config);
            t.start();
            return t;
        }

        @Override
        public void onChange(AttachmentConfigTracker.Change change) {
            changes.add(new ChangeResult(change.changeType(), change.attachment().childPath()));
        }

        public void assertAdded(int... path) {
            assertOne(ChangeType.ADDED, path);
        }

        public void assertRemoved(int... path) {
            assertOne(ChangeType.REMOVED, path);
        }

        public void assertChanged(int... path) {
            assertOne(ChangeType.CHANGED, path);
        }

        public void assertOne(ChangeType change, int... path) {
            if (changes.isEmpty()) {
                fail("Expected " + change + " "  + pathStr(path) + " but no changes happened");
            }
            ChangeResult c = changes.remove(0);
            if (c.change != change || !Arrays.equals(c.path, path)) {
                System.err.println("Expected: " + change + " " + pathStr(path));
                System.err.println("But got: " + c.change + " " + pathStr(c.path));
                fail("Expected " + change + " "  + pathStr(path) + ", but got " +
                        c.change + " " + pathStr(c.path));
            }
        }

        public void assertNone() {
            if (!changes.isEmpty()) {
                ChangeResult c = changes.remove(0);
                fail("Expected no changes, but got " +
                        c.change + " " + pathStr(c.path));
            }
        }

        public void log() {
            System.err.println("Changes:");
            for (ChangeResult c : changes) {
                System.err.println("- " + c.change + " " + pathStr(c.path));
            }
        }

        private static class ChangeResult {
            private final ChangeType change;
            private final int[] path;

            public ChangeResult(ChangeType change, int[] path) {
                this.change = change;
                this.path = path;
            }
        }

        private static String pathStr(int[] path) {
            if (path.length == 0) {
                return "ROOT";
            }
            StringBuilder str = new StringBuilder();
            for (int p : path) {
                str.append('[').append(p).append(']');
            }
            return str.toString();
        }
    }
}