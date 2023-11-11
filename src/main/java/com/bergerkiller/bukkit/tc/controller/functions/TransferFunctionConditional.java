package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionItem;

/**
 * Compares an input against a constant right-hand side threshold.
 */
public class TransferFunctionConditional implements TransferFunction {
    public static final Serializer<TransferFunctionConditional> SERIALIZER = new Serializer<TransferFunctionConditional>() {
        @Override
        public String typeId() {
            return "CONDITIONAL";
        }

        @Override
        public String title() {
            return "Conditional";
        }

        @Override
        public TransferFunctionConditional createNew(TransferFunctionHost host) {
            return new TransferFunctionConditional();
        }

        @Override
        public TransferFunctionConditional load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionConditional conditional = new TransferFunctionConditional();
            if (config.isNode("left")) {
                conditional.leftInput.setFunction(host.loadFunction(config.getNode("left")));
            }
            conditional.rightInput = config.getOrDefault("right", conditional.rightInput);
            conditional.operator = config.getOrDefault("operator", conditional.operator);
            if (config.isNode("falseOutput")) {
                conditional.falseOutput.setFunction(host.loadFunction(config.getNode("falseOutput")));
            }
            if (config.isNode("trueOutput")) {
                conditional.trueOutput.setFunction(host.loadFunction(config.getNode("trueOutput")));
            }
            return conditional;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionConditional conditional) {
            if (!conditional.leftInput.isIdentity()) {
                config.set("left", host.saveFunction(conditional.leftInput.getFunction()));
            }
            config.set("right", conditional.rightInput);
            config.set("operator", conditional.operator);
            if (!conditional.falseOutput.isIdentity()) {
                config.set("falseOutput", host.saveFunction(conditional.falseOutput.getFunction()));
            }
            if (!conditional.trueOutput.isIdentity()) {
                config.set("trueOutput", host.saveFunction(conditional.trueOutput.getFunction()));
            }
        }
    };

    /** Left-hand side of the comparator operation. Supports functions. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> leftInput = TransferFunction.Holder.of(TransferFunction.identity());
    /** Right-hand side of the comparator operation */
    private double rightInput = 0.0;
    /** Operator to use when comparing the left and right hand inputs */
    private Operator operator = Operator.GREATER_EQUAL_THAN;
    /** Function to call when the condition is false. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> falseOutput = TransferFunction.Holder.of(TransferFunction.identity());
    /** Function to call when the condition is true. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> trueOutput = TransferFunction.Holder.of(TransferFunction.identity());

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public double map(double input) {
        boolean result = operator.compare(leftInput.getFunction().map(input), rightInput);
        return (result ? trueOutput : falseOutput).getFunction().map(input);
    }

    public void setLeftInput(TransferFunction input) {
        this.leftInput.setFunction(input);
    }

    public void setRightInput(double input) {
        this.rightInput = input;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public void setFalseOutput(TransferFunction output) {
        this.falseOutput.setFunction(output);
    }

    public void setTrueOutput(TransferFunction output) {
        this.trueOutput.setFunction(output);
    }

    @Override
    public TransferFunctionConditional clone() {
        TransferFunctionConditional copy = new TransferFunctionConditional();
        copy.leftInput.setFunction(this.leftInput.getFunction().clone());
        copy.rightInput = this.rightInput;
        copy.operator = this.operator;
        copy.falseOutput.setFunction(this.falseOutput.getFunction().clone());
        copy.trueOutput.setFunction(this.trueOutput.getFunction().clone());
        return copy;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        String str = operator.title();
        if (operator.hasRightHandSide()) {
            str = str + rightInput + " [cond]";
        } else {
            str += " [cond]";
        }
        view.draw(MapFont.MINECRAFT, 2, 3, MapColorPalette.COLOR_GREEN, str);
    }

    @Override
    public void openDialog(MapWidgetTransferFunctionDialog dialog) {

    }

    /**
     * Comparator operator mode
     */
    public enum Operator {
        EQUAL("==", (l, r) -> l == r),
        NOT_EQUAL("!=", (l, r) -> l != r),
        GREATER_THAN(">", (l, r) -> l > r),
        GREATER_EQUAL_THAN(">=", (l, r) -> l >= r),
        LESSER_THAN("<", (l, r) -> l < r),
        LESSER_EQUAL_THAN("<=", (l, r) -> l <= r),
        BOOL("!= 0", (l, r) -> l != 0.0);

        private final String title;
        private final DoubleComparator comparator;

        Operator(String title, DoubleComparator comparator) {
            this.title = title;
            this.comparator = comparator;
        }

        public String title() {
            return title;
        }

        public boolean compare(double left, double right) {
            return comparator.compare(left, right);
        }

        public boolean hasRightHandSide() {
            return this != BOOL;
        }
    }

    @FunctionalInterface
    private interface DoubleComparator {
        boolean compare(double left, double right);
    }
}
