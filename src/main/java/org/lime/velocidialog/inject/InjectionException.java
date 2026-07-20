package org.lime.velocidialog.inject;

/** Indicates an unsupported Velocity/JVM runtime that cannot safely be transformed. */
public final class InjectionException extends RuntimeException {
    public InjectionException(final String message) {
        super(message);
    }

    public InjectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

