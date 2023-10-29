package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

import java.util.Arrays;
import java.util.List;

/**
 * Describes how an input value is transformed into an output value using a curve,
 * with control points guiding the interpolation. Points beyond the ends of the
 * curve are clamped and set to the value at those ends.
 */
public class TransferFunctionCurve implements TransferFunction, Cloneable {
    public static final Serializer<TransferFunctionCurve> SERIALIZER = new Serializer<TransferFunctionCurve>() {
        @Override
        public String typeId() {
            return "CURVE_GRAPH";
        }

        @Override
        public String title() {
            return "Curve Graph";
        }

        @Override
        public TransferFunctionCurve createNew(TransferFunctionHost host) {
            return empty();
        }

        @Override
        public TransferFunctionCurve load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionCurve curve = empty();
            for (String value : config.getList("values", String.class)) {
                int sep = value.indexOf('=');
                if (sep != -1) {
                    int inputEnd = sep;
                    int outputStart = sep + 1;
                    while (inputEnd > 0 && value.charAt(inputEnd) == ' ') {
                        inputEnd--;
                    }
                    while (outputStart < value.length() && value.charAt(outputStart) == ' ') {
                        outputStart++;
                    }

                    String inputTxt = value.substring(0, inputEnd).trim();
                    String outputTxt = value.substring(outputStart).trim();
                    try {
                        double input = Double.parseDouble(inputTxt);
                        double output = Double.parseDouble(outputTxt);
                        curve.add(input, output);
                    } catch (NumberFormatException ex) { /* ignore */ }
                }
            }
            return curve;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionCurve curve) {
            if (!curve.isEmpty()) {
                List<String> values = config.getList("values", String.class);
                for (int i = 0; i < curve.size(); i++) {
                    values.add(curve.getInput(i) + " = " + curve.getOutput(i));
                }
            }
        }
    };

    /** Stores the inputs, followed by the outputs. Length is always a multiple of two. */
    private double[] v;
    /** Tracks the previous input sent to {@link #map(double)} */
    private double previousInput = Double.NaN;
    /** Tracks whether in the past, input was increasing or not */
    private boolean inputIncreasing = false;

    /**
     * Returns a default, empty curve. All inputs will map 1:1 to the outputs
     *
     * @return Empty envelope
     */
    public static TransferFunctionCurve empty() {
        return new TransferFunctionCurve(new double[0]);
    }

    public static Builder builder() {
        return new Builder();
    }

    private TransferFunctionCurve(double[] v) {
        this.v = v;
    }

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    /**
     * Gets whether this curve is empty
     *
     * @return True if empty
     */
    public boolean isEmpty() {
        return v.length == 0;
    }

    /**
     * Gets the number of input-output mapping entries stored in this curve
     *
     * @return Size
     */
    public int size() {
        return v.length >> 1;
    }

    /**
     * Gets the input of the input-output mapping at the index specified
     * @param index Index to get at
     * @return Input value of the entry
     * @throws IndexOutOfBoundsException If index is out of bounds
     */
    public double getInput(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        return v[index];
    }

    /**
     * Gets the output of the input-output mapping at the index specified
     *
     * @param index Index to get at
     * @return Output value of the entry
     * @throws IndexOutOfBoundsException If index is out of bounds
     */
    public double getOutput(int index) {
        int len = v.length >> 1;
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        return v[index + len];
    }

    /**
     * Removes the input-output mapping at the index specified
     *
     * @param index Index to remove at
     * @throws IndexOutOfBoundsException If index is out of bounds
     */
    public void removeAt(int index) {
        int len = v.length >> 1;
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        } else if (len == 1) {
            this.v = new double[0];
            return;
        }

        double[] new_v = new double[(len - 1) << 1];

        // Input
        {
            System.arraycopy(this.v, 0, new_v, 0, index);
            System.arraycopy(this.v, index + 1, new_v, index, len - index - 1);
        }

        // Output
        {
            System.arraycopy(this.v, len, new_v, len - 1, index);
            System.arraycopy(this.v, len + index + 1, new_v, len + index - 1, len - index - 1);
        }

        this.v = new_v;
    }

    /**
     * Adds or inserts a new input-output mapping to this curve.
     * If an entry already exists at an input, at most inserts one more entry
     * to allow for instant mapped changes. If two input mapping already exist
     * with the same input, overwrites one of the two previous outputs instead
     * of inserting a third.
     *
     * @param input Input value
     * @param output Mapped output value
     * @return Index of the input-output mapping
     */
    public int add(double input, double output) {
        int len = v.length >> 1;
        if (len == 0) {
            v = new double[] { input, output };
            return 0;
        }
        int index = Arrays.binarySearch(v, 0, len, input);
        if (index < 0) {
            // Insert a new entry here
            index = -index - 1;
            insertAt(index, input, output);
            return index;
        }

        // Check whether this value is stored in two positions already
        // If not, insert an additional entry to make up for this
        if (index > 0 && v[index - 1] == input) {
            --index;
        } else if (index >= (len-1) || v[index + 1] != input) {
            insertAt(index, input, v[index]);
            len++;
        }

        // See which of the two values is nearest to the output value requested
        double dv0 = Math.abs(v[index + len] - output);
        double dv1 = Math.abs(v[index + len + 1] - output);

        // If same distance, consider entries neighbouring the two entries instead
        if (dv0 == dv1) {
            if (index > 0) {
                dv0 = Math.abs(v[index + len - 1] - output);
            }
            if (index < (len - 1)) {
                dv1 = Math.abs(v[index + len + 1] - output);
            }
        }

        // Update either of the two entries
        if (dv0 < dv1) {
            v[index + len] = output;
            return index;
        } else {
            v[index + len + 1] = output;
            return index + 1;
        }
    }

    private void insertAt(int index, double input, double output) {
        int len = v.length >> 1;
        double[] new_v = new double[(len + 1) << 1];

        // Input
        {
            System.arraycopy(this.v, 0, new_v, 0, index);
            new_v[index] = input;
            System.arraycopy(this.v, index, new_v, index + 1, len - index);
        }

        // Output
        {
            System.arraycopy(this.v, len, new_v, len + 1, index);
            new_v[index + len + 1] = output;
            System.arraycopy(this.v, len + index, new_v, len + index + 2, len - index);
        }

        this.v = new_v;
    }

    /**
     * Attempts to update the input and output of an existing entry.
     * Clamps the input to the neighbouring entries to avoid changing order.
     * If the neighbouring entry already has two entries, cancels the change
     * and returns false. Otherwise, returns true.
     *
     * @param index Index of the entry to update
     * @param input New input for the entry mapping
     * @param output New output for the entry mapping
     * @return True if the entry was updated, False otherwise
     * @throws IndexOutOfBoundsException If index is out of bounds
     */
    public boolean updateAt(int index, double input, double output) {
        int len = v.length >> 1;
        if (index < 0 || index >= len) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }

        if (input < v[index] && index > 0) {
            // Clamp input with preceding entry
            // If there's another entry before that with the same value, cancel
            double preceding = v[index - 1];
            if (input <= preceding) {
                input = preceding;
                if (index > 1 && v[index - 2] == preceding) {
                    return false;
                }
            }
        } else if (input > v[index] && index < (len - 1)) {
            // Clamp input with following entry
            // If there's another entry before that with the same value, cancel
            double preceding = v[index + 1];
            if (input >= preceding) {
                input = preceding;
                if (index < (len - 2) && v[index + 2] == preceding) {
                    return false;
                }
            }
        }

        // OK
        v[index] = input;
        v[index + len] = output;
        return true;
    }

    /**
     * Maps the input value to an output value based on this curve.
     * If this curve has no control points configured, returns the input
     * value as-is (invalid curve). If it has only one control point,
     * that set value is always returned as a constant.<br>
     * <br>
     * If at the input two mappings exist, uses the previous input value
     * with hysteresis to decide which of the two values to return.
     * This way the value will 'hold' until going beyond the threshold.
     * If no previous mapping call was done, returns the value mapped to
     * the highest input value (assumes input will increase from that point)
     *
     * @param input Input value
     * @return Transfer Function Output value
     */
    @Override
    public double map(double input) {
        // Track whether input is increasing or not
        // Initial value is assumed to be decreasing so that it picks the highest mapped value
        {
            double previous = this.previousInput;
            this.previousInput = input;
            if (Double.isNaN(previous) || input < previous) {
                this.inputIncreasing = false;
            } else if (input > previous) {
                this.inputIncreasing = true;
            }
        }

        int len = v.length >> 1;
        if (len == 0) {
            return input;
        } else if (len == 1) {
            return v[1];
        }
        int index = Arrays.binarySearch(v, 0, len, input);

        // Exact match
        if (index >= 0) {
            // If two entries exist at this input, decide based on whether the input is increasing
            // If it was increasing upon reaching this input, pick the lower bound (hysteresis)
            // If it was decreasing upon reaching this input, pick the higher bound
            if (index > 0 && v[index - 1] == input) {
                if (inputIncreasing) {
                    --index;
                }
            } else if (index < (len - 1) && v[index + 1] == input) {
                if (!inputIncreasing) {
                    ++index;
                }
            }

            return v[index + len];
        }

        index = -index - 1;

        // Before first element
        if (index == 0) {
            return v[len];
        }

        // After last element
        if (index == len) {
            return v[2 * len - 1];
        }

        // Linear interpolation
        double input_t0 = v[index - 1];
        double input_t1 = v[index];
        double theta = (input_t1 - input) / (input_t1 - input_t0);
        return v[len+index-1] * (1.0 - theta) + v[len+index] * theta;
    }

    /**
     * Sends all input-output mapping entries to the consumer specified
     *
     * @param consumer Consumer to accept all inputs and outputs
     */
    public void forEach(EntryConsumer consumer) {
        int len = v.length >> 1;
        for (int i = 0; i < len; i++) {
            consumer.accept(v[i], v[i + len]);
        }
    }

    @Override
    public TransferFunctionCurve clone() {
        return new TransferFunctionCurve(v.clone());
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 2, 3, MapColorPalette.COLOR_GREEN, "Curve");
    }

    @Override
    public void openDialog(MapWidgetTransferFunctionDialog dialog) {
        dialog.addWidget(new MapWidgetButton() {
            @Override
            public void onActivate() {
            }
        }.setText("Click").setBounds(5, 5, 80, 13));
    }

    @FunctionalInterface
    public interface EntryConsumer {
        void accept(double input, double output);
    }

    public static class Builder {
        private final TransferFunctionCurve curve = empty();

        public Builder add(double input, double output) {
            curve.add(input, output);
            return this;
        }

        public TransferFunctionCurve build() {
            return curve;
        }
    }
}
