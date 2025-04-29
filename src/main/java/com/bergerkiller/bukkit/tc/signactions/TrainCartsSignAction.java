package com.bergerkiller.bukkit.tc.signactions;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A SignAction for signs that use the [train] and/or [cart] syntax, with an
 * identifier put on the second line. Only this syntax is supported.
 * Some optimizations are employed to do this matching more efficiently.
 *
 * @see com.bergerkiller.bukkit.tc.signactions.util.SignActionLookupMap SignActionLookupMap
 */
public abstract class TrainCartsSignAction extends SignAction {
    private final String[] typeIdentifiers;

    /**
     * Constructs a new TrainCartsSignAction
     *
     * @param typeIdentifiers Identifiers passed to {@link SignActionEvent#isType(String...)} to
     *                        uniquely identify this sign action. Must be all-lowercase.
     */
    public TrainCartsSignAction(String... typeIdentifiers) {
        if (typeIdentifiers.length == 0) {
            throw new IllegalArgumentException("Must have at least one unique type identifier set");
        }
        this.typeIdentifiers = typeIdentifiers;
    }

    /**
     * Gets a List of all-lowercases type identifiers that uniquely identify this sign action.
     * If the line on the second line starts with one of these identifiers, this sign action is
     * chosen to handle it.
     *
     * @return Type Identifiers
     */
    public final List<String> getTypeIdentifiers() {
        return Collections.unmodifiableList(Arrays.asList(typeIdentifiers));
    }

    @Override
    public final boolean match(SignActionEvent event) {
        return event.getMode() != SignActionMode.NONE && event.isType(this.typeIdentifiers);
    }
}
