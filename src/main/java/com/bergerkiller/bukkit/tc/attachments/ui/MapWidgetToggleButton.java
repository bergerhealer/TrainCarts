package com.bergerkiller.bukkit.tc.attachments.ui;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import com.bergerkiller.bukkit.common.map.widgets.MapWidgetButton;
import com.bergerkiller.bukkit.common.utils.LogicUtil;

/**
 * Button that toggles between different options that were previously added
 */
public abstract class MapWidgetToggleButton<T> extends MapWidgetButton {
    private final Map<T, String> _values = new LinkedHashMap<T, String>();
    private T _value = null;

    /**
     * Callback called when the selected option is changed.
     * Is only fired after the widget has been attached.
     */
    public abstract void onSelectionChanged();

    /**
     * Adds an option to this toggle button
     * 
     * @param value Value of the option
     * @param text Text displayed when the option is active
     * @return this widget
     */
    public MapWidgetToggleButton<T> addOption(T value, String text) {
        if (this._values.isEmpty()) {
            this._value = value;
            this.setText(text);
            if (this.getDisplay() != null) {
                this.onSelectionChanged();
            }
        }
        this._values.put(value, text);
        return this;
    }

    /**
     * Adds multiple options to this toggle button, making use of the text function
     * to generate the text of each option.
     * 
     * @param textFunction Function that transforms each option into text
     * @param values Possible option values
     * @return this widget
     */
    @SafeVarargs
    public final MapWidgetToggleButton<T> addOptions(Function<T, String> textFunction, T... values) {
        for (T value : values) {
            addOption(value, textFunction.apply(value));
        }
        return this;
    }

    /**
     * Adds multiple options to this toggle button, making use of the text function
     * to generate the text of each option. The type must be an enum.
     * 
     * @param textFunction Function that transforms each option into text
     * @param enumType Enum class from which the enum constants are added as options
     * @return this widget
     */
    public final MapWidgetToggleButton<T> addOptions(Function<T, String> textFunction, Class<T> enumType) {
        return addOptions(textFunction, enumType.getEnumConstants());
    }

    /**
     * Sets the option that is currently selected and visible
     * 
     * @param value Selected option value to set to
     * @return this widget
     */
    public MapWidgetToggleButton<T> setSelectedOption(T value) {
        String text = this._values.get(value);
        if (text == null) {
            throw new IllegalArgumentException("Value " + value + " is not a valid option");
        }
        if (!LogicUtil.bothNullOrEqual(this._value, value)) {
            this._value = value;
            this.setText(text);
            if (this.getDisplay() != null) {
                this.onSelectionChanged();
            }
        }
        return this;
    }

    /**
     * Gets the current option that is selected
     * 
     * @return selected option
     */
    public T getSelectedOption() {
        return this._value;
    }

    /**
     * Switches to the next option in sequential order, looping around
     */
    public void nextOption() {
        if (this._values.size() > 1) {
            Iterator<Map.Entry<T, String>> iter = this._values.entrySet().iterator();
            while (true) {
                if (!iter.hasNext()) {
                    Map.Entry<T, String> e = this._values.entrySet().iterator().next();
                    this._value = e.getKey();
                    this.setText(e.getValue());
                    break;
                } else if (iter.next().getKey().equals(this._value) && iter.hasNext()) {
                    Map.Entry<T, String> e = iter.next();
                    this._value = e.getKey();
                    this.setText(e.getValue());
                    break;
                }
            }

            if (this.getDisplay() != null) {
                this.onSelectionChanged();
            }
        }
    }

    @Override
    public void onActivate() {
        this.nextOption();
    }
}
