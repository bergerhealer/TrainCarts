package com.bergerkiller.bukkit.tc.signactions.util;

import com.bergerkiller.bukkit.common.Common;
import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.signactions.SignActionRegisterEvent;
import com.bergerkiller.bukkit.tc.events.signactions.SignActionUnregisterEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * A very simple unoptimized version of the SignActionLookupMap.
 * Used for benchmark testing primarily, but is very reliable.
 */
class SignActionLookupMapBasicImpl implements SignActionLookupMap {
    private final List<SimpleEntry> entries = new ArrayList<>();

    @Override
    public Optional<Entry> lookup(SignActionEvent event, LookupMode lookupMode) {
        for (SimpleEntry entry : entries) {
            SignAction action = entry.action;
            if (lookupMode.test(entry) && action.match(event) && action.verify(event)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    @Override
    public <T extends SignAction> T register(T action, boolean priority) {
        if (priority) {
            entries.add(0, new SimpleEntry(action));
        } else {
            entries.add(new SimpleEntry(action));
        }

        // Fire unregister event so that all signs can be refreshed on the server
        // Extra guards so that this also works under test
        if (!Common.IS_TEST_MODE) {
            CommonUtil.callEvent(new SignActionRegisterEvent(action, priority));
        }

        return action;
    }

    @Override
    public void unregister(SignAction action) {
        for (Iterator<SimpleEntry> iter = entries.iterator(); iter.hasNext();) {
            SimpleEntry entry = iter.next();
            if (entry.action.equals(action)) {
                iter.remove();

                // Fire unregister event so that all signs can be refreshed on the server
                // Extra guards so that this also works under test
                if (!Common.IS_TEST_MODE) {
                    CommonUtil.callEvent(new SignActionUnregisterEvent(action));
                }
                return;
            }
        }
    }

    private static class SimpleEntry implements Entry {
        public final SignAction action;
        public final boolean hasLoadedChangedHandler;

        public SimpleEntry(SignAction action) {
            this.action = action;
            this.hasLoadedChangedHandler = CommonUtil.isMethodOverrided(SignAction.class, action.getClass(), "loadedChanged", SignActionEvent.class, boolean.class);
        }

        @Override
        public SignAction action() {
            return action;
        }

        @Override
        public boolean hasLoadedChangedHandler() {
            return hasLoadedChangedHandler;
        }
    }
}
