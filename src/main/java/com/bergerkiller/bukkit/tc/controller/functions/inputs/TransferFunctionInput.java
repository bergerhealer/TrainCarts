package com.bergerkiller.bukkit.tc.controller.functions.inputs;

import com.bergerkiller.bukkit.common.math.Matrix4x4;
import com.bergerkiller.bukkit.tc.attachments.ui.MapWidgetSelectionBox;
import com.bergerkiller.bukkit.tc.attachments.ui.functions.MapWidgetTransferFunctionDialog;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunction;
import com.bergerkiller.bukkit.tc.controller.functions.TransferFunctionHost;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an input from an external source. Must be further implemented based on what
 * input is read.
 */
public abstract class TransferFunctionInput implements TransferFunction {
    private ReferencedSource source = ReferencedSource.NONE;

    /**
     * Initializes the ReferencedSource which will produce the final value returned by
     * this transfer function input. There is no guarantee that the returned source
     * will actually be used. The source should not have any reference to this transfer
     * function input.<br>
     * <br>
     * If the returned source is already used by another input, then that source is re-used
     * and the returned one is discarded.
     *
     * @param host TransferFunctionHost that will manage the source
     * @return new ReferencedSource
     */
    public abstract ReferencedSource createSource(TransferFunctionHost host);

    /**
     * Calls {@link #createSource(TransferFunctionHost)} and registers the referenced source
     * into this input. This method should be called every time this transfer function input
     * is changed, or after loading the function.
     *
     * @param host Host that will hold the referenced source and keep it updated
     */
    public final void updateSource(TransferFunctionHost host) {
        ReferencedSource newSource = this.createSource(host);
        newSource = host.registerInputSource(newSource);
        if (!this.source.equals(newSource)) {
            this.source.removeRecipient(this);
            this.source = newSource;
            newSource.addRecipient(this);
        }
    }

    @Override
    public double map(double unusedInput) {
        return source.value();
    }

    @Override
    public boolean isBooleanOutput() {
        return source.isBool();
    }

    @Override
    public final TransferFunctionInput clone() {
        TransferFunctionInput copy = cloneInput();
        if (!copy.source.equals(source)) {
            copy.source = source;
            copy.source.addRecipient(this);
        }
        return copy;
    }

    protected abstract TransferFunctionInput cloneInput();

    @Override
    public void openDialog(MapWidgetTransferFunctionDialog dialog) {
        // Inputs by default show a selection button to switch between different categories of
        // input transforms. Changing this button will change the entire instance of the transfer
        // function.
        dialog.addWidget(new MapWidgetSelectionBox() {
            private final List<TransferFunction.Serializer<?>> serializers = new ArrayList<>();
            private boolean loading = false;

            @Override
            public void onAttached() {
                serializers.clear();
                loading = true;
                for (TransferFunction.Serializer<?> serializer : dialog.getHost().getRegistry().all()) {
                    if (serializer.isListed() && serializer.isInput()) {
                        serializers.add(serializer);
                        addItem(serializer.title());
                        if (serializer == TransferFunctionInput.this.getSerializer()) {
                            setSelectedIndex(getItemCount() - 1);
                        }
                    }
                }
                super.onAttached();
                loading = false;
                this.focus();
            }

            @Override
            public void onSelectedItemChanged() {
                if (!loading && getSelectedIndex() >= 0 && getSelectedIndex() < serializers.size()) {
                    TransferFunction.Serializer<?> newSerializer = serializers.get(getSelectedIndex());
                    dialog.setFunction(newSerializer.createNew(dialog.getHost()));
                }
            }
        }).setBounds(4, 5, dialog.getWidth() - 8, 11);
    }

    /**
     * A reference source of an input value for a {@link TransferFunctionInput}. This source implementation
     * is unique, in that the attachments system calls callbacks into it to update the value. This avoids
     * issues in a multithreaded environment.<br>
     * <br>
     * Every source tracks what transfer function inputs make use of it, and automatically cleans itself
     * up when no longer used.
     */
    public static abstract class ReferencedSource {
        /** Temporary value used until a proper source is set for an input */
        public static final ReferencedSource NONE = new ReferencedSource() {
            @Override
            public boolean isBool() {
                return false;
            }

            @Override
            public boolean equals(Object o) {
                return this == o;
            }

            @Override
            public void addRecipient(TransferFunctionInput recipient) {
            }
        };

        protected double value = 0.0;
        private final List<WeakReference<TransferFunctionInput>> recipients = new ArrayList<>();

        /**
         * Gets the current, last-updated value of this input
         *
         * @return Last value
         */
        public double value() {
            return value;
        }

        /**
         * Gets whether this input is a boolean input. A boolean input returns 1.0 for true and
         * 0.0 for false. For conditional transfer functions this method signals whether to show
         * the comparator controls.
         *
         * @return True if this input is a boolean input, False if not
         */
        public abstract boolean isBool();

        /**
         * Adds a new recipient for this input
         *
         * @param recipient TransferFunctionInput recipient to add
         */
        public void addRecipient(TransferFunctionInput recipient) {
            recipients.add(new WeakReference<>(recipient));
        }

        /**
         * Removes a recipient that was previously added to this input
         *
         * @param recipient TransferFunctionInput recipient to remove
         */
        public void removeRecipient(TransferFunctionInput recipient) {
            recipients.removeIf(ref -> {
                TransferFunctionInput input = ref.get();
                return input == null || input == recipient;
            });
        }

        /**
         * Checks whether there are any transfer function inputs remaining that are a recipient
         * of this input. Automatically forgets recipients when they are garbage-collected.
         *
         * @return True if this input has recipients, and therefore should be kept updated
         */
        public boolean hasRecipients() {
            recipients.removeIf(ref -> ref.get() == null);
            return !recipients.isEmpty();
        }

        /**
         * Called every tick
         */
        public void onTick() {
        }

        /**
         * Called every time the attachment is transformed
         *
         * @param transform
         */
        public void onTransform(Matrix4x4 transform) {
        }

        /**
         * Whether this input equals another one. Must be implemented for correct functioning
         * of the input registry.
         *
         * @param o Object
         * @return True if object is equal to this one
         */
        @Override
        public abstract boolean equals(Object o);
    }
}
