package org.lime.velocidialog.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.VelocidialogBridge;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VelocityInjectorCloseTest {
    @AfterEach
    void clearBridge() {
        VelocidialogBridge.clear();
    }

    @Test
    void resetsBeforeClearingBridgeAndClosesIdempotently() throws Exception {
        final AtomicInteger handlerCalls = new AtomicInteger();
        VelocidialogBridge.install(
                (player, dialog) -> handlerCalls.incrementAndGet(),
                player -> { },
                (player, packet) -> false,
                (player, buffer) -> false);
        final Instrumentation instrumentation = instrumentationProxy();
        final AtomicReference<Instrumentation> resetInstrumentation = new AtomicReference<>();
        final AtomicInteger resetCalls = new AtomicInteger();
        final ResettableClassFileTransformer transformer = transformer((actual, strategy) -> {
            resetCalls.incrementAndGet();
            resetInstrumentation.set(actual);
            assertSame(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION, strategy);
            VelocidialogBridge.show(new Object(), new Object());
            return true;
        });
        final VelocityInjector injector = injector(instrumentation, transformer);

        injector.close();
        assertSame(instrumentation, resetInstrumentation.get());
        assertEquals(1, resetCalls.get());
        assertEquals(1, handlerCalls.get(), "handler must remain installed while reset is running");

        VelocidialogBridge.show(new Object(), new Object());
        assertEquals(1, handlerCalls.get(), "bridge must be clear after a successful reset");
        injector.close();
        assertEquals(1, resetCalls.get());
    }

    @Test
    void failedResetKeepsBridgeLiveAndCloseCanBeRetried() throws Exception {
        final AtomicInteger handlerCalls = new AtomicInteger();
        VelocidialogBridge.install(
                (player, dialog) -> handlerCalls.incrementAndGet(),
                player -> { },
                (player, packet) -> false,
                (player, buffer) -> false);
        final AtomicBoolean resetSucceeds = new AtomicBoolean(false);
        final AtomicInteger resetCalls = new AtomicInteger();
        final ResettableClassFileTransformer transformer = transformer((instrumentation, strategy) -> {
            resetCalls.incrementAndGet();
            return resetSucceeds.get();
        });
        final VelocityInjector injector = injector(instrumentationProxy(), transformer);

        final InjectionException failure = assertThrows(InjectionException.class, injector::close);
        assertTrue(failure.getMessage().contains("did not reset"));
        VelocidialogBridge.show(new Object(), new Object());
        assertEquals(1, handlerCalls.get(), "transformed code still needs a live bridge");

        resetSucceeds.set(true);
        injector.close();
        assertEquals(2, resetCalls.get());
        VelocidialogBridge.show(new Object(), new Object());
        assertEquals(1, handlerCalls.get());
    }

    private static VelocityInjector injector(
            final Instrumentation instrumentation,
            final ResettableClassFileTransformer transformer) throws Exception {
        final Constructor<VelocityInjector> constructor = VelocityInjector.class.getDeclaredConstructor(
                Instrumentation.class, ResettableClassFileTransformer.class, java.lang.reflect.Method.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                instrumentation, transformer,
                VelocidialogBridge.class.getMethod("clear"));
    }

    private static Instrumentation instrumentationProxy() {
        return (Instrumentation) Proxy.newProxyInstance(
                VelocityInjectorCloseTest.class.getClassLoader(),
                new Class<?>[] {Instrumentation.class},
                (proxy, method, arguments) -> defaultValue(method.getReturnType()));
    }

    private static ResettableClassFileTransformer transformer(final ResetBehavior reset) {
        return (ResettableClassFileTransformer) Proxy.newProxyInstance(
                VelocityInjectorCloseTest.class.getClassLoader(),
                new Class<?>[] {ResettableClassFileTransformer.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("reset") && arguments != null && arguments.length == 2) {
                        return reset.reset(
                                (Instrumentation) arguments[0],
                                (AgentBuilder.RedefinitionStrategy) arguments[1]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return 0;
    }

    @FunctionalInterface
    private interface ResetBehavior {
        boolean reset(Instrumentation instrumentation, AgentBuilder.RedefinitionStrategy strategy);
    }
}
