package org.lime.velocidialog.api.dialog.internal;

import java.util.Objects;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal validation shared by the immutable public model.
 */
@ApiStatus.Internal
public final class DialogValidation {
    private DialogValidation() {
    }

    public static <T> T requireNonNull(final T value, final String field) {
        return Objects.requireNonNull(value, field);
    }

    public static int requireRange(final int value, final String field, final int min, final int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("argument " + field + " must be in [" + min + ", " + max + "]: " + value);
        }
        return value;
    }

    public static float requireRange(final float value, final String field, final float min, final float max) {
        if (Float.compare(value, min) >= 0 && Float.compare(value, max) <= 0) {
            return value;
        }
        throw new IllegalArgumentException("argument " + field + " must be in [" + min + ", " + max + "]: " + value);
    }

    public static int requirePositive(final int value, final String field) {
        if (value < 1) {
            throw new IllegalArgumentException("argument " + field + " must be positive: " + value);
        }
        return value;
    }

    public static float requirePositive(final float value, final String field) {
        if (Float.compare(value, 0.0F) > 0 && Float.compare(value, Float.MAX_VALUE) <= 0) {
            return value;
        }
        throw new IllegalArgumentException("argument " + field + " must be positive: " + value);
    }

    /**
     * Mirrors Minecraft's StringTemplate variable-name rule: letters, digits and underscores.
     * Minecraft intentionally considers the empty name valid.
     */
    public static String requireInputKey(final String key) {
        requireNonNull(key, "key");
        for (int i = 0; i < key.length(); i++) {
            final char character = key.charAt(i);
            if (character != '_' && !Character.isLetterOrDigit(character)) {
                throw new IllegalArgumentException("key must be a valid input name: " + key);
            }
        }
        return key;
    }

    /**
     * Validates Minecraft's command-template form and requires at least one {@code $(name)} variable.
     */
    public static String requireCommandTemplate(final String template) {
        requireNonNull(template, "template");
        boolean foundVariable = false;
        int index = 0;
        while (index < template.length()) {
            final int opening = template.indexOf("$(", index);
            if (opening < 0) {
                break;
            }
            final int closing = template.indexOf(')', opening + 2);
            if (closing < 0) {
                throw new IllegalArgumentException("unclosed command template variable");
            }
            final String variable = template.substring(opening + 2, closing);
            requireInputKey(variable);
            foundVariable = true;
            index = closing + 1;
        }
        if (!foundVariable) {
            throw new IllegalArgumentException("command template must contain at least one variable");
        }
        return template;
    }
}
