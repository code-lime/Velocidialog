package org.lime.velocidialog.codec;

/** Thrown when an Adventure value cannot be represented by the native dialog codec. */
public final class DialogEncodingException extends IllegalArgumentException {
    public DialogEncodingException(final String message) {
        super(message);
    }

    public DialogEncodingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
