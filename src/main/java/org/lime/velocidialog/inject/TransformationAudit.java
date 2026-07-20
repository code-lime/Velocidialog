package org.lime.velocidialog.inject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/** Captures transformer failures that the JVM is allowed to swallow during retransformation. */
final class TransformationAudit extends AgentBuilder.Listener.Adapter {
    private final Set<String> expectedTypes;
    private final Set<String> transformedTypes = new HashSet<>();
    private final List<Failure> failures = new ArrayList<>();

    TransformationAudit(final Set<String> expectedTypes) {
        this.expectedTypes = Set.copyOf(expectedTypes);
    }

    @Override
    public synchronized void onTransformation(
            final TypeDescription typeDescription,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded,
            final DynamicType dynamicType) {
        final String name = typeDescription.getName();
        if (expectedTypes.contains(name)) {
            transformedTypes.add(name);
        }
    }

    @Override
    public synchronized void onError(
            final String typeName,
            final ClassLoader classLoader,
            final JavaModule module,
            final boolean loaded,
            final Throwable throwable) {
        if (expectedTypes.contains(typeName)) {
            failures.add(new Failure(typeName, Objects.requireNonNull(throwable, "throwable")));
        }
    }

    /** Checks errors from installation and starts a fresh explicit-retransform observation. */
    synchronized void prepareVerification() {
        throwIfFailed("initial transformation");
        transformedTypes.clear();
    }

    /** Requires every target to have produced transformed bytes during explicit retransformation. */
    synchronized void verify() {
        throwIfFailed("explicit retransformation");
        final Set<String> missing = new LinkedHashSet<>(expectedTypes);
        missing.removeAll(transformedTypes);
        if (!missing.isEmpty()) {
            throw new InjectionException(
                    "Byte Buddy did not transform required Velocity classes: " + missing);
        }
    }

    private void throwIfFailed(final String phase) {
        if (failures.isEmpty()) {
            return;
        }
        final Failure first = failures.getFirst();
        final InjectionException failure = new InjectionException(
                "Byte Buddy failed during " + phase + " for " + first.typeName(), first.cause());
        for (int index = 1; index < failures.size(); index++) {
            final Failure additional = failures.get(index);
            failure.addSuppressed(new InjectionException(
                    "Additional transformation failure for " + additional.typeName(),
                    additional.cause()));
        }
        throw failure;
    }

    private record Failure(String typeName, Throwable cause) {
    }
}
