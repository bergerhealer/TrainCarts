package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.ui.list.MapWidgetTransferFunctionList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

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
                //TODO: Replace with getNodeList(path, false) when BKCommonLib 1.20.2-v3 or later is a hard-depend
                List<Object> effectConfigs = config.getList("functions");
                TransferFunctionRegistry registry = host.getRegistry();
                for (Item item : list.getItems()) {
                    ConfigurationNode functionConfig = registry.save(host, item.getFunction());
                    if (item.mode() != FunctionMode.ASSIGN) {
                        functionConfig.set("functionMode", item.mode());
                    }
                    effectConfigs.add(functionConfig);
                }
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
        return SERIALIZER;
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
    public boolean isPure() {
        for (int i = items.size() - 1; i >= 0; --i) {
            Item item = items.get(i);
            if (!item.getFunction().isPure()) {
                return false;
            } else if (item.mode() == FunctionMode.ASSIGN) {
                break; // Assigned. None of the other items are used.
            }
        }

        return true;
    }

    /**
     * Gets whether a particular item of this list has a boolean output result.
     * This takes the function mode of that item into account.
     *
     * @param index Index of the item. A value of -1 will return the result of
     *              the isBooleanInput parameter.
     * @param isBooleanInput Whether the input to this list is a boolean
     * @return True if this item has a boolean output
     */
    public boolean isBooleanOutput(int index, BooleanSupplier isBooleanInput) {
        BooleanSupplier chain = isBooleanInput;
        int itemCount = items.size();
        for (int i = 0; i < itemCount; ++i) {
            Item item = items.get(i);
            if (item.mode.booleanMode() == FunctionBooleanMode.INPUT) {
                // Infer function output
                final BooleanSupplier prev = chain;
                chain = () -> item.function.isBooleanOutput(prev);
            } else {
                // Always true/false
                final boolean result = item.mode.booleanMode().asBool();
                chain = () -> result;
            }
            if (i == index) {
                break;
            }
        }
        return chain.getAsBoolean();
    }

    @Override
    public boolean isBooleanOutput(BooleanSupplier isBooleanInput) {
        return isBooleanOutput(items.size() - 1, isBooleanInput);
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
        byte color = widget.defaultColor(MapColorPalette.COLOR_GREEN);

        view.drawLine(0, 3, 6, 3, color);
        view.drawLine(0, 5, 6, 5, color);
        view.drawLine(0, 7, 6, 7, color);
        view.drawLine(0, 9, 6, 9, color);
        view.draw(MapFont.MINECRAFT, 8, 3, color,
                "[" + items.size() + (items.size() == 1 ? " step]" : " steps]"));
    }

    @Override
    public void openDialog(Dialog dialog) {
        dialog.addWidget(new MapWidgetTransferFunctionList((MapWidgetTransferFunctionDialog) dialog, this) {
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
    public static class Item extends TransferFunction.Holder<TransferFunction> implements Cloneable {
        private final FunctionMode mode;

        public Item(FunctionMode mode, TransferFunction function) {
            super(function, false);
            this.mode = mode;
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
        ASSIGN((i, fo) -> fo, FunctionBooleanMode.INPUT),
        MULTIPLY((i, fo) -> i * fo, FunctionBooleanMode.NEVER),
        DIVIDE((i, fo) -> i / fo, FunctionBooleanMode.NEVER),
        SUBTRACT((i, fo) -> i - fo, FunctionBooleanMode.NEVER),
        ADD((i, fo) -> i + fo, FunctionBooleanMode.NEVER),
        OR((i, fo) -> (i != 0.0 || fo != 0.0) ? 1.0 : 0.0, FunctionBooleanMode.ALWAYS),
        AND((i, fo) -> (i != 0.0 && fo != 0.0) ? 1.0 : 0.0, FunctionBooleanMode.ALWAYS);

        private final FunctionModeOperator operator;
        private final FunctionBooleanMode booleanMode;

        FunctionMode(FunctionModeOperator operator, FunctionBooleanMode booleanMode) {
            this.operator = operator;
            this.booleanMode = booleanMode;
        }

        public FunctionBooleanMode booleanMode() {
            return booleanMode;
        }

        public double apply(double input, double functionOutput) {
            return operator.apply(input, functionOutput);
        }
    }

    /**
     * The operator result mode for boolean inputs
     */
    public enum FunctionBooleanMode {
        /** State of input is copied */
        INPUT(false /* shouldn't be used */),
        /** Result is always a boolean */
        ALWAYS(true),
        /** Result is never a boolean, always a number */
        NEVER(false);

        private final boolean asBool;

        FunctionBooleanMode(boolean asBool) {
            this.asBool = asBool;
        }

        public boolean asBool() {
            return asBool;
        }
    }

    @FunctionalInterface
    private interface FunctionModeOperator {
        double apply(double input, double functionOutput);
    }
}
