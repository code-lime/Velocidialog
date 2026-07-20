package org.lime.velocidialog.inject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.instrument.Instrumentation;
import org.junit.jupiter.api.Test;

class InstrumentationAccessTest {
    @Test
    void selfAttachObtainsRetransformationCapableInstrumentation() {
        final Instrumentation instrumentation = InstrumentationAccess.acquire();
        assertNotNull(instrumentation);
        assertTrue(instrumentation.isRetransformClassesSupported());
    }
}
