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
                list.add(registry.load(host, functionConfig));
            }
            return list;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionList list) {
            if (!list.isEmpty()) {
                List<ConfigurationNode> savedConfigs = new ArrayList<>(list.size());
                TransferFunctionRegistry registry = host.getRegistry();
                for (TransferFunction function : list.getFunctions()) {
                    savedConfigs.add(registry.save(host, function));
                }
                config.setNodeList("functions", savedConfigs);
            }
        }
    };

    private final List<TransferFunction> functions = new ArrayList<>();

    // These make sure it remembers the scroll position / focused item when exiting the
    // menu (to configure something) and going back. It's not persistent.
    private int lastSelectedFunctionIndex = -1;
    private int lastScrollPosition = 0;

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return null;
    }

    public List<TransferFunction> getFunctions() {
        return Collections.unmodifiableList(functions);
    }

    public int size() {
        return functions.size();
    }

    public boolean isEmpty() {
        return functions.isEmpty();
    }

    public TransferFunction get(int index) {
        return functions.get(index);
    }

    public void add(TransferFunction function) {
        functions.add(function);
    }

    public void add(int index, TransferFunction function) {
        functions.add(index, function);
    }

    public void remove(int index) {
        functions.remove(index);
    }

    public int indexOf(TransferFunction function) {
        return functions.indexOf(function);
    }

    @Override
    public double map(double input) {
        for (TransferFunction function : functions) {
            input = function.map(input);
        }
        return input;
    }

    @Override
    public boolean isBooleanOutput() {
        return !functions.isEmpty() && functions.get(functions.size() - 1).isBooleanOutput();
    }

    @Override
    public TransferFunctionList clone() {
        TransferFunctionList copy = new TransferFunctionList();
        for (TransferFunction func : functions) {
            copy.functions.add(func.clone());
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
}
