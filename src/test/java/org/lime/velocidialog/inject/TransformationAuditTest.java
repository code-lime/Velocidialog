package org.lime.velocidialog.inject;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

class TransformationAuditTest {
    @Test
    void escalatesTransformerErrorsThatInstrumentationMaySwallow() {
        final TransformationAudit audit = new TransformationAudit(Set.of(String.class.getName()));
        final IllegalStateException transformFailure = new IllegalStateException("broken advice");

        audit.onError(String.class.getName(), String.class.getClassLoader(),
                JavaModule.ofType(String.class), true, transformFailure);

        final InjectionException failure = assertThrows(
                InjectionException.class, audit::prepareVerification);
        assertSame(transformFailure, failure.getCause());
        assertTrue(failure.getMessage().contains(String.class.getName()));
    }

    @Test
    void requiresEveryTargetDuringTheExplicitRetransformationPass() {
        final TransformationAudit audit = new TransformationAudit(Set.of(
                String.class.getName(), Integer.class.getName()));
        audit.prepareVerification();
        audit.onTransformation(TypeDescription.ForLoadedType.of(String.class),
                String.class.getClassLoader(), JavaModule.ofType(String.class), true, null);

        final InjectionException failure = assertThrows(InjectionException.class, audit::verify);
        assertTrue(failure.getMessage().contains(Integer.class.getName()));
    }

    @Test
    void acceptsACompleteErrorFreeRetransformationPass() {
        final TransformationAudit audit = new TransformationAudit(Set.of(
                String.class.getName(), Integer.class.getName()));
        audit.prepareVerification();
        audit.onTransformation(TypeDescription.ForLoadedType.of(String.class),
                String.class.getClassLoader(), JavaModule.ofType(String.class), true, null);
        audit.onTransformation(TypeDescription.ForLoadedType.of(Integer.class),
                Integer.class.getClassLoader(), JavaModule.ofType(Integer.class), true, null);

        audit.verify();
    }
}
