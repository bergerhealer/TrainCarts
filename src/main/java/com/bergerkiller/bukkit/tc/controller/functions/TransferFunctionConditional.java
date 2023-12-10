package com.bergerkiller.bukkit.tc.controller.functions;

import com.bergerkiller.bukkit.common.config.ConfigurationNode;
import com.bergerkiller.bukkit.common.events.map.MapKeyEvent;
import com.bergerkiller.bukkit.common.map.MapCanvas;
import com.bergerkiller.bukkit.common.map.MapColorPalette;
import com.bergerkiller.bukkit.common.map.MapFont;
import com.bergerkiller.bukkit.common.map.MapPlayerInput;
import com.bergerkiller.bukkit.common.map.widgets.MapWidget;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionItem;
import com.bergerkiller.bukkit.tc.controller.functions.ui.MapWidgetTransferFunctionSingleItem;
import com.bergerkiller.bukkit.tc.controller.functions.ui.conditional.MapWidgetTransferFunctionConditionalHysteresis;
import com.bergerkiller.bukkit.tc.controller.functions.ui.conditional.MapWidgetTransferFunctionConditionalOperator;
import com.bergerkiller.bukkit.tc.utils.CachedBooleanSupplier;

import java.util.function.BooleanSupplier;

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
            TransferFunctionConditional conditional = new TransferFunctionConditional();
            conditional.setOperator(Operator.GREATER_THAN);
            conditional.setRightInput(TransferFunctionConstant.zero());
            conditional.setTrueOutput(TransferFunctionBoolean.TRUE);
            conditional.setFalseOutput(TransferFunctionBoolean.FALSE);
            return conditional;
        }

        @Override
        public TransferFunctionConditional load(TransferFunctionHost host, ConfigurationNode config) {
            TransferFunctionConditional conditional = new TransferFunctionConditional();
            if (config.isNode("left")) {
                conditional.setLeftInput(host.loadFunction(config.getNode("left")));
            }
            if (config.isNode("right")) {
                conditional.setRightInput(host.loadFunction(config.getNode("right")));
            }
            conditional.setOperator(config.getOrDefault("operator", conditional.operator));
            conditional.setHysteresis(config.getOrDefault("hysteresis", 0.0));
            if (config.isNode("falseOutput")) {
                conditional.setFalseOutput(host.loadFunction(config.getNode("falseOutput")));
            }
            if (config.isNode("trueOutput")) {
                conditional.setTrueOutput(host.loadFunction(config.getNode("trueOutput")));
            }
            return conditional;
        }

        @Override
        public void save(TransferFunctionHost host, ConfigurationNode config, TransferFunctionConditional conditional) {
            if (!conditional.leftInput.isDefault()) {
                config.set("left", host.saveFunction(conditional.leftInput.getFunction()));
            }
            if (!conditional.rightInput.isDefault()) {
                config.set("right", host.saveFunction(conditional.rightInput.getFunction()));
            }
            config.set("operator", conditional.operator);
            config.set("hysteresis", conditional.hysteresis != 0.0 ? conditional.hysteresis : null);
            if (!conditional.falseOutput.isDefault()) {
                config.set("falseOutput", host.saveFunction(conditional.falseOutput.getFunction()));
            }
            if (!conditional.trueOutput.isDefault()) {
                config.set("trueOutput", host.saveFunction(conditional.trueOutput.getFunction()));
            }
        }
    };

    /** Left-hand side of the comparator operation. Supports functions. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> leftInput = TransferFunction.Holder.of(TransferFunction.identity(), true);
    /** Right-hand side of the comparator operation. Supports functions. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> rightInput = TransferFunction.Holder.of(TransferFunction.identity(), true);
    /** Operator to use when comparing the left and right hand inputs */
    private Operator operator = Operator.GREATER_THAN;
    /** Optional hysteresis, requiring this much change before switching true/false states */
    private double hysteresis = 0.0;
    /** Keeps track of previous true/false state for handling hysteresis. Inverted default for negative hysteresis. */
    private Boolean hysteresisLastState = null;
    /** Function to call when the condition is false. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> falseOutput = TransferFunction.Holder.of(TransferFunction.identity(), true);
    /** Function to call when the condition is true. Input is passed to it. */
    private final TransferFunction.Holder<TransferFunction> trueOutput = TransferFunction.Holder.of(TransferFunction.identity(), true);

    @Override
    public Serializer<? extends TransferFunction> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public double map(double input) {
        boolean result;
        if (operator == Operator.BOOL) {
            result = leftInput.getFunction().map(input) != 0.0;
        } else if (hysteresis == 0.0) {
            // Simplified
            result = operator.compare(leftInput.getFunction().map(input),
                                      rightInput.getFunction().map(input));
        } else {
            // Initial last state is decided based on the hysteresis being positive or not
            // Positive hysteresis (usual) will use an initial state of 'false' (off)
            if (hysteresisLastState == null) {
                hysteresisLastState = (this.hysteresis < 0.0);
            }

            // Uses hysteresis, which is more complicated
            // This means the value must change enough before transitioning state
            result = operator.compareWithHysteresis(hysteresisLastState,
                                                    leftInput.getFunction().map(input),
                                                    rightInput.getFunction().map(input),
                                                    Math.abs(this.hysteresis));
        }
        hysteresisLastState = result;
        return (result ? trueOutput : falseOutput).getFunction().map(input);
    }

    @Override
    public boolean isBooleanOutput(BooleanSupplier isBooleanInput) {
        isBooleanInput = CachedBooleanSupplier.of(isBooleanInput);
        return trueOutput.getFunction().isBooleanOutput(isBooleanInput) &&
               falseOutput.getFunction().isBooleanOutput(isBooleanInput);
    }

    @Override
    public boolean isPure() {
        return leftInput.getFunction().isPure()
                && (operator == Operator.BOOL || rightInput.getFunction().isPure())
                && falseOutput.getFunction().isPure()
                && trueOutput.getFunction().isPure();
    }

    public void setLeftInput(TransferFunction input) {
        this.leftInput.setFunction(input);
    }

    public void setRightInput(TransferFunction input) {
        this.rightInput.setFunction(input);
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public void setHysteresis(double hysteresis) {
        this.hysteresis = hysteresis;
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
        copy.rightInput.setFunction(this.rightInput.getFunction().clone());
        copy.operator = this.operator;
        copy.falseOutput.setFunction(this.falseOutput.getFunction().clone());
        copy.trueOutput.setFunction(this.trueOutput.getFunction().clone());
        return copy;
    }

    @Override
    public void drawPreview(MapWidgetTransferFunctionItem widget, MapCanvas view) {
        view.draw(MapFont.MINECRAFT, 2, 3, MapColorPalette.COLOR_GREEN, "Conditional [Y:N]");
    }

    @Override
    public void openDialog(Dialog dialog) {
        // Cache whether input is a boolean - won't change while this function is being configured
        BooleanSupplier isBooleanInput = CachedBooleanSupplier.of(dialog::isBooleanInput);

        // Helper function that focuses the operator widget
        final Runnable focusOperatorWidget = () -> {
            for (MapWidget w : dialog.getWidget().getWidgets()) {
                if (w instanceof MapWidgetTransferFunctionConditionalOperator) {
                    w.focus();
                    break;
                }
            }
        };

        // Condition input
        // Note: hysteresis and right-input must be done first as other functions depend on it
        {
            dialog.addLabel(39, 3, MapColorPalette.COLOR_RED, "CONDITION");

            // Left side input
            dialog.addWidget(new MapWidgetTransferFunctionSingleItem(dialog.getHost(), leftInput, isBooleanInput) {
                @Override
                public void onChanged(Holder<TransferFunction> function) {
                    dialog.markChanged();
                }

                @Override
                public TransferFunction createDefault() {
                    return TransferFunction.identity();
                }

                @Override
                public void onKeyPressed(MapKeyEvent event) {
                    if (event.getKey() == MapPlayerInput.Key.DOWN && isFocused()) {
                        focusOperatorWidget.run();
                    } else {
                        super.onKeyPressed(event);
                    }
                }
            }).setBounds(5, 9, dialog.getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            // Hysteresis
            final MapWidgetTransferFunctionConditionalHysteresis hysteresisWidget;
            hysteresisWidget = dialog.addWidget(new MapWidgetTransferFunctionConditionalHysteresis(hysteresis) {
                @Override
                public void onHysteresisChanged(double hysteresis) {
                    TransferFunctionConditional.this.setHysteresis(hysteresis);
                    dialog.markChanged();
                }
            });
            hysteresisWidget.setBounds(dialog.getWidth() - 55, 26, 50, 13);

            // Right side input
            final MapWidgetTransferFunctionSingleItem rightInputWidget;
            rightInputWidget = dialog.addWidget(new MapWidgetTransferFunctionSingleItem(dialog.getHost(), rightInput, isBooleanInput) {
                @Override
                public void onChanged(Holder<TransferFunction> function) {
                    dialog.markChanged();
                }

                @Override
                public TransferFunction createDefault() {
                    return TransferFunction.identity();
                }

                @Override
                public void onKeyPressed(MapKeyEvent event) {
                    if (event.getKey() == MapPlayerInput.Key.UP && isFocused()) {
                        focusOperatorWidget.run();
                    } else {
                        super.onKeyPressed(event);
                    }
                }
            });
            rightInputWidget.setBounds(5, 41, dialog.getWidth() - 10, MapWidgetTransferFunctionItem.HEIGHT);

            final Runnable operatorChangeHandler = () -> {
                hysteresisWidget.setVisible(operator != Operator.BOOL);
                rightInputWidget.setVisible(operator != Operator.BOOL);
            };
            operatorChangeHandler.run(); // Initial

            // Operator
            dialog.addWidget(new MapWidgetTransferFunctionConditionalOperator(operator) {
                @Override
                public void onOperatorChanged(Operator operator) {
                    TransferFunctionConditional.this.operator = operator;
                    operatorChangeHandler.run();
                    dialog.markChanged();
                }
            }).setBounds(5, 26, 21, 13);
        }

        // Result true/false
        {
            dialog.addLabel(44, dialog.getHeight() - 44, MapColorPalette.COLOR_RED, "RESULT");

            dialog.addLabel(3, dialog.getHeight() - 33, MapColorPalette.COLOR_RED, "Y");
            dialog.addWidget(new MapWidgetTransferFunctionSingleItem(dialog.getHost(), trueOutput, isBooleanInput) {
                @Override
                public void onChanged(Holder<TransferFunction> function) {
                    dialog.markChanged();
                }

                @Override
                public TransferFunction createDefault() {
                    return TransferFunction.identity();
                }
            }).setBounds(7, dialog.getHeight() - 38, dialog.getWidth() - 12, MapWidgetTransferFunctionItem.HEIGHT);

            dialog.addLabel(3, dialog.getHeight() - 16, MapColorPalette.COLOR_RED, "N");
            dialog.addWidget(new MapWidgetTransferFunctionSingleItem(dialog.getHost(), falseOutput, isBooleanInput) {
                @Override
                public void onChanged(Holder<TransferFunction> function) {
                    dialog.markChanged();
                }

                @Override
                public TransferFunction createDefault() {
                    return TransferFunction.identity();
                }
            }).setBounds(7, dialog.getHeight() - 21, dialog.getWidth() - 12, MapWidgetTransferFunctionItem.HEIGHT);
        }
    }

    /**
     * Comparator operator mode
     */
    public enum Operator {
        EQUAL("==",
                /* Condition */
                (l, r) -> l == r,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> l == r,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> Math.abs(l - r) > h),
        NOT_EQUAL("!=",
                /* Condition */
                (l, r) -> l != r,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> Math.abs(l - r) > h,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> l == r),
        GREATER_THAN(">",
                /* Condition */
                (l, r) -> l > r,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> (l - r) > h,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> (r - l) >= h),
        GREATER_EQUAL_THAN(">=",
                /* Condition */
                (l, r) -> l >= r,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> (l - r) >= h,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> (r - l) > h),
        LESSER_THAN("<",
                /* Condition */
                (l, r) -> l < r,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> (r - l) > h,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> (l - r) >= h),
        LESSER_EQUAL_THAN("<=",
                /* Condition */
                (l, r) -> l <= r,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> (r - l) >= h,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> (l - r) > h),
        BOOL("!=0",
                /* Condition */
                (l, r) -> l != 0.0,
                /* Hysteresis Condition for false -> true */
                (l, r, h) -> l != 0.0,
                /* Hysteresis Condition for true -> false */
                (l, r, h) -> l == 0.0);

        private final String title;
        private final DoubleComparator comparator;
        private final DoubleHysteresisComparator trueHysteresisComparator;
        private final DoubleHysteresisComparator falseHysteresisComparator;

        Operator(
                String title,
                DoubleComparator comparator,
                DoubleHysteresisComparator trueHysteresisComparator,
                DoubleHysteresisComparator falseHysteresisComparator
        ) {
            this.title = title;
            this.comparator = comparator;
            this.trueHysteresisComparator = trueHysteresisComparator;
            this.falseHysteresisComparator = falseHysteresisComparator;
        }

        public String title() {
            return title;
        }

        public boolean compare(double left, double right) {
            return comparator.compare(left, right);
        }

        public boolean compareWithHysteresis(boolean wasTrue, double left, double right, double hysteresis) {
            if (wasTrue) {
                return !falseHysteresisComparator.compare(left, right, hysteresis);
            } else {
                return trueHysteresisComparator.compare(left, right, hysteresis);
            }
        }

        public boolean hasRightHandSide() {
            return this != BOOL;
        }
    }

    @FunctionalInterface
    private interface DoubleComparator {
        boolean compare(double left, double right);
    }

    @FunctionalInterface
    private interface DoubleHysteresisComparator {
        boolean compare(double left, double right, double hysteresis);
    }
}
