package com.bergerkiller.bukkit.tc.controller.functions;

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
    private final List<TransferFunction> functions = new ArrayList<>();

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
    public void makeDialog(MapWidgetTransferFunctionDialog dialog) {
        dialog.addWidget(new MapWidgetTransferFunctionList(dialog, this));
    }
}
