package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configures a list of transfer functions that are processed in sequence
 */
public class TransferFunctionList implements TransferFunction, Cloneable {
    public static final Serializer<TransferFunctionList> SERIALIZER = new Serializer<TransferFunctionList>() {
        @Override
        public String typeId() {
            return "LIST";
        }

        @Override
        public String title() {
            return "List Sequence";
        }

        @Override
        public TransferFunctionList createNew(TransferFunctionHost host) {
            return new TransferFunctionList();
        }

        @Override
        public TransferFunctionList load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionList list = new TransferFunctionList();
            TransferFunctionRegistry registry = host.getRegistry();
            for (ConfigurationNode functionConfig : config.getNodeList("functions")) {
                TransferFunction function = registry.load(host, functionConfig);
                FunctionMode mode = functionConfig.getOrDefault("functionMode", FunctionMode.ASSIGN);
                list.add(new Item(mode, function));
            }
            return list;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionList list) {
            if (!list.isEmpty()) {
                List<ConfigurationNode> savedConfigs = new ArrayList<>(list.size());
                TransferFunctionRegistry registry = host.getRegistry();
                for (Item item : list.getItems()) {
                    ConfigurationNode functionConfig = registry.save(host, item.function());
                    if (item.mode() != FunctionMode.ASSIGN) {
                        functionConfig.set("functionMode", item.mode());
                    }
                    savedConfigs.add(functionConfig);
                }
                config.setNodeList("functions", savedConfigs);
            }
        }
    };

    private final List<Item> items = new ArrayList<>();

    // These make sure it remembers the scroll position / focused item when exiting the
    // menu (to configure something) and going back. It's not persistent.
    private int lastSelectedFunctionIndex = -1;
    private int lastScrollPosition = 0;

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return null;
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Item get(int index) {
        return items.get(index);
    }

    public void set(int index, Item item) {
        items.set(index, item);
    }

    public void add(TransferFunction function) {
        add(new Item(FunctionMode.ASSIGN, function));
    }

    public void add(Item item) {
        items.add(item);
    }

    public void add(int index, Item item) {
        items.add(index, item);
    }

    public void remove(int index) {
        items.remove(index);
    }

    public int indexOf(Item item) {
        return items.indexOf(item);
    }

    @Override
    public double map(double input) {
        for (Item item : items) {
            input = item.map(input);
        }
        return input;
    }

    @Override
    public boolean isBooleanOutput() {
        return !items.isEmpty() && items.get(items.size() - 1).function().isBooleanOutput();
    }

    @Override
    public TransferFunctionList clone() {
        TransferFunctionList copy = new TransferFunctionList();
        for (Item item : items) {
            copy.items.add(item.clone());
        }
        return copy;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {

    }

    @Override
    public void openDialog(MapWidgetTransferFunctionDialog dialog) {
        dialog.addWidget(new MapWidgetTransferFunctionList(dialog, this) {
            @Override
            public void onSelectedItemChanged() {
                lastSelectedFunctionIndex = getSelectedItemIndex();
            }

            @Override
            public void onTick() {
                super.onTick();
                lastScrollPosition = getVScroll();
            }
        }.setSelectedItemIndex(this.lastSelectedFunctionIndex)
         .setVScroll(lastScrollPosition));
    }

    /**
     * A single processed function in a list
     */
    public static class Item implements Cloneable {
        private final FunctionMode mode;
        private final TransferFunction function;

        public Item(FunctionMode mode, TransferFunction function) {
            this.mode = mode;
            this.function = function;
        }

        /**
         * Gets what is done with the result of the function
         *
         * @return Function Mode
         */
        public FunctionMode mode() {
            return mode;
        }

        /**
         * Gets the function that is called
         *
         * @return Transfer Function
         */
        public TransferFunction function() {
            return function;
        }

        /**
         * Calls {@link TransferFunction#map(double)}
         *
         * @param input Input to the mapper
         * @return Function output, with {@link #mode()} applied
         */
        public double map(double input) {
            return mode.apply(input, function.map(input));
        }

        @Override
        public Item clone() {
            return new Item(mode, function.clone());
        }
    }

    /**
     * The way a single item in the function list modifies the value at that point.
     */
    public enum FunctionMode {
        ASSIGN((i, fo) -> fo),
        MULTIPLY((i, fo) -> i * fo),
        DIVIDE((i, fo) -> i / fo),
        SUBTRACT((i, fo) -> i - fo),
        ADD((i, fo) -> i + fo);

        private final FunctionModeOperator operator;

        FunctionMode(FunctionModeOperator operator) {
            this.operator = operator;
        }

        public double apply(double input, double functionOutput) {
            return operator.apply(input, functionOutput);
        }
    }

    @FunctionalInterface
    private interface FunctionModeOperator {
        double apply(double input, double functionOutput);
    }
}
