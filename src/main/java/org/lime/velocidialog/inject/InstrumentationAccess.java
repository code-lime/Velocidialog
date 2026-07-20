package org.lime.velocidialog.inject;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;

/** Obtains Instrumentation through Byte Buddy's runtime attachment mechanism. */
final class InstrumentationAccess {
    private InstrumentationAccess() {
    }

    static Instrumentation acquire() {
        try {
            return ByteBuddyAgent.install();
        } catch (final RuntimeException | LinkageError error) {
            throw new InjectionException(
                    "Unable to obtain JVM Instrumentation through Byte Buddy dynamic attachment. "
                            + "This JVM must permit dynamic agent loading.", error);
        }
    }
}
