package org.lime.velocidialog.inject;

import java.lang.instrument.Instrumentation;

/** Minimal child-JVM entry point used to verify the actual shaded artifact. */
public final class PackagedInstrumentationForkMain {
    private PackagedInstrumentationForkMain() {
    }

    public static void main(final String[] arguments) throws Exception {
        final Instrumentation instrumentation = InstrumentationAccess.acquire();
        if (!instrumentation.isRetransformClassesSupported()) {
            throw new AssertionError("Retransformation is unavailable");
        }

        Class.forName("org.lime.velocidialog.internal.bytebuddy.agent.builder.AgentBuilder");
        try {
            Class.forName("net.bytebuddy.agent.builder.AgentBuilder");
            throw new AssertionError("Byte Buddy core leaked into its canonical package");
        } catch (final ClassNotFoundException expected) {
            // Correct: only the small standalone attachment artifact remains canonical.
        }

        System.out.println("PACKAGED_INSTRUMENTATION_OK");
    }
}
