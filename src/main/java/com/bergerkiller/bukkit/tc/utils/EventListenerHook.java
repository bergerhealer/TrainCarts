package com.bergerkiller.bukkit.tc.utils;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;

import com.bergerkiller.bukkit.common.utils.CommonUtil;
import com.bergerkiller.mountiplex.reflection.ClassHook;

/**
 * Installs event listener hooks to intercept the handling of events.
 * Debug use only.
 */
public class EventListenerHook {

    public static void unhook(Class<? extends Event> eventClass) {
        hook(eventClass, null);
    }

    public static <T extends Event> void hook(Class<T> eventClass, Handler<T> handler) {
        HandlerList handlerlist = CommonUtil.getEventHandlerList(eventClass);
        if (handlerlist == null) {
            throw new IllegalArgumentException("Event class " + eventClass.getName()
                    + " has no HandlerList");
        }

        // Retrieve the internal map storing the event listeners
        EnumMap<EventPriority, List<RegisteredListener>> map;
        try {
            Field f = HandlerList.class.getDeclaredField("handlerslots");
            boolean wasAccessible = f.isAccessible();
            f.setAccessible(true);
            map = CommonUtil.unsafeCast(f.get(handlerlist));
            f.setAccessible(wasAccessible);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to modify HandlerList", t);
        }

        // Create a mutator for the RegisteredListener objects
        // When a listener is set, hook each listener and handle it
        // When no listener is set,
        Function<RegisteredListener, RegisteredListener> mutator;
        if (handler != null) {
            final Hook hook = new Hook(handler);
            mutator = l -> hook.hook(ClassHook.unhook(l));
        } else {
            mutator = l -> Hook.unhook(l);
        }

        synchronized (handlerlist) {
            for (List<RegisteredListener> list : map.values()) {
                ListIterator<RegisteredListener> iter = list.listIterator();
                while (iter.hasNext()) {
                    iter.set(mutator.apply(iter.next()));
                }
            }

            try {
                Field f = HandlerList.class.getDeclaredField("handlers");
                boolean wasAccessible = f.isAccessible();
                f.setAccessible(true);
                f.set(handlerlist, null);
                f.setAccessible(wasAccessible);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to modify HandlerList", t);
            }
        }

        handlerlist.bake();
    }

    @FunctionalInterface
    public static interface Handler<T extends Event> {
        void handle(RegisteredListener listener, Consumer<Event> callEvent, T event);
    }

    public static class Hook extends ClassHook<Hook> {
        private final Handler<Event> handler;

        private Hook(Handler<?> handler) {
            this.handler = CommonUtil.unsafeCast(handler);
        }

        @HookMethod("public void callEvent(org.bukkit.event.Event event)")
        public void callEvent(Event event) {
            RegisteredListener listener = (RegisteredListener) instance();
            this.handler.handle(listener, base::callEvent, event);
        }
    }
}
