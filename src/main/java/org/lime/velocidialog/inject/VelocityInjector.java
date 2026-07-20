package org.lime.velocidialog.inject;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.velocitypowered.api.proxy.Player;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatcher;

/** Installs and owns all transformations required to implement Velocity's dialog methods. */
public final class VelocityInjector implements AutoCloseable {
    private static final String BRIDGE = "com.velocitypowered.api.proxy.VelocidialogBridge";
    private static final String CONFIG_HANDLER =
            "com.velocitypowered.proxy.connection.client.ClientConfigSessionHandler";
    private static final String CONFIG_PACKET =
            "com.velocitypowered.proxy.protocol.packet.ServerboundCustomClickActionPacket";
    private static final String PLAY_HANDLER =
            "com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler";
    private static final String BYTE_BUF = "io.netty.buffer.ByteBuf";
    private static final String AUDIENCE = "net.kyori.adventure.audience.Audience";
    private static final String DIALOG_LIKE = "net.kyori.adventure.dialog.DialogLike";

    private final Instrumentation instrumentation;
    private final ResettableClassFileTransformer transformer;
    private final Method clearBridge;
    private boolean reset;
    private volatile boolean closed;

    private VelocityInjector(
            final Instrumentation instrumentation,
            final ResettableClassFileTransformer transformer,
            final Method clearBridge) {
        this.instrumentation = instrumentation;
        this.transformer = transformer;
        this.clearBridge = clearBridge;
    }

    public static VelocityInjector install(
            final BiConsumer<Object, Object> show,
            final Consumer<Object> close,
            final BiPredicate<Object, Object> configurationClick,
            final BiPredicate<Object, Object> playClick) {
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(close, "close");
        Objects.requireNonNull(configurationClick, "configurationClick");
        Objects.requireNonNull(playClick, "playClick");

        final Instrumentation instrumentation = InstrumentationAccess.acquire();
        final RuntimeTypes types = verifyRuntime(instrumentation);
        final Class<?> bridge = injectBridge(types.loader());

        try {
            bridge.getMethod("install", BiConsumer.class, Consumer.class,
                            BiPredicate.class, BiPredicate.class)
                    .invoke(null, show, close, configurationClick, playClick);
        } catch (final ReflectiveOperationException error) {
            throw new InjectionException("Unable to initialize the Velocity classloader bridge", error);
        }

        ResettableClassFileTransformer transformer = null;
        try {
            final TransformationAudit audit = new TransformationAudit(Set.of(
                    AUDIENCE, Player.class.getName(), CONFIG_PACKET, PLAY_HANDLER));
            transformer = createAgentBuilder(types.loader(), audit).installOn(instrumentation);
            audit.prepareVerification();
            verifyTransformed(instrumentation, types, audit);
            return new VelocityInjector(instrumentation, transformer, bridge.getMethod("clear"));
        } catch (final Throwable error) {
            Throwable cleanupFailure = null;
            boolean clearHandlers = transformer == null;
            if (transformer != null) {
                try {
                    clearHandlers = transformer.reset(instrumentation,
                            AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
                    if (!clearHandlers) {
                        cleanupFailure = new InjectionException(
                                "Byte Buddy did not roll back Velocity dialog transformations");
                    }
                } catch (final RuntimeException | LinkageError resetFailure) {
                    cleanupFailure = new InjectionException(
                            "Unable to roll back Velocity dialog transformations", resetFailure);
                }
            }
            if (clearHandlers) {
                try {
                    bridge.getMethod("clear").invoke(null);
                } catch (final ReflectiveOperationException clearFailure) {
                    cleanupFailure = new InjectionException(
                            "Unable to clear the Velocity classloader bridge after rollback", clearFailure);
                }
            }
            if (cleanupFailure != null) {
                error.addSuppressed(cleanupFailure);
            }
            if (error instanceof InjectionException injectionException) {
                throw injectionException;
            }
            throw new InjectionException("Unable to transform Velocity dialog entry points", error);
        }
    }

    private static RuntimeTypes verifyRuntime(final Instrumentation instrumentation) {
        if (!instrumentation.isRetransformClassesSupported()) {
            throw new InjectionException("This JVM does not support class retransformation");
        }

        final ClassLoader loader = Player.class.getClassLoader();
        try {
            final Class<?> audience = Class.forName(AUDIENCE, false, loader);
            final Class<?> dialogLike = Class.forName(DIALOG_LIKE, false, loader);
            final Method audienceShow = audience.getDeclaredMethod("showDialog", dialogLike);
            final Method audienceClose = audience.getDeclaredMethod("closeDialog");
            final Method playerShow = Player.class.getMethod("showDialog", dialogLike);
            final Method playerClose = Player.class.getMethod("closeDialog");
            if (audienceShow.getReturnType() != void.class
                    || audienceClose.getReturnType() != void.class
                    || playerShow.getReturnType() != void.class
                    || playerClose.getReturnType() != void.class) {
                throw new InjectionException("Adventure Audience dialog method descriptors changed");
            }

            final Class<?> configPacket = Class.forName(CONFIG_PACKET, false, loader);
            requirePacketHandle(configPacket);
            final Class<?> config = Class.forName(CONFIG_HANDLER, false, loader);
            requirePlayerField(config);

            final Class<?> byteBuf = Class.forName(BYTE_BUF, false, loader);
            final Class<?> play = Class.forName(PLAY_HANDLER, false, loader);
            final Method playHandle = play.getMethod("handleUnknown", byteBuf);
            if (playHandle.getReturnType() != void.class) {
                throw new InjectionException("Velocity PLAY unknown-packet handler descriptor changed");
            }
            requirePlayerField(play);

            final Class<?>[] targets = {audience, Player.class, configPacket, play};
            for (final Class<?> target : targets) {
                if (!instrumentation.isModifiableClass(target)) {
                    throw new InjectionException("JVM reports " + target.getName()
                            + " as non-modifiable");
                }
            }
            return new RuntimeTypes(loader, audience, configPacket, play);
        } catch (final ClassNotFoundException | NoSuchMethodException error) {
            throw new InjectionException(
                    "Unsupported Velocity internals: required 3.4/4.0 dialog entry points are missing",
                    error);
        }
    }

    private static void requirePlayerField(final Class<?> type) {
        try {
            final Field field = type.getDeclaredField("player");
            if (field.getType().isPrimitive()) {
                throw new InjectionException(type.getName() + ".player unexpectedly became primitive");
            }
        } catch (final NoSuchFieldException error) {
            throw new InjectionException(type.getName() + " no longer exposes the expected player field", error);
        }
    }

    private static void requirePacketHandle(final Class<?> packetType) {
        for (final Method method : packetType.getDeclaredMethods()) {
            if (method.getName().equals("handle")
                    && method.getParameterCount() == 1
                    && method.getReturnType() == boolean.class) {
                return;
            }
        }
        throw new InjectionException(
                "Velocity CONFIG custom-click packet handle descriptor changed");
    }

    private static Class<?> injectBridge(final ClassLoader loader) {
        try {
            return Class.forName(BRIDGE, false, loader);
        } catch (final ClassNotFoundException expected) {
            // Continue with injection.
        }

        final String resource = '/' + BRIDGE.replace('.', '/') + ".class";
        final byte[] bytes;
        try (InputStream input = VelocityInjector.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new InjectionException("Bridge class bytes are absent from the plugin jar");
            }
            bytes = input.readAllBytes();
        } catch (final IOException error) {
            throw new InjectionException("Unable to read bridge class bytes", error);
        }

        try {
            final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    Player.class, MethodHandles.lookup());
            final Class<?> injected = ClassInjector.UsingLookup.of(lookup)
                    .injectRaw(Collections.singletonMap(BRIDGE, bytes)).get(BRIDGE);
            if (injected == null || injected.getClassLoader() != loader) {
                throw new InjectionException("Bridge was not defined in Velocity's classloader");
            }
            return injected;
        } catch (final IllegalAccessException | RuntimeException error) {
            // A concurrent/reloaded installation may have won the defineClass race.
            try {
                return Class.forName(BRIDGE, false, loader);
            } catch (final ClassNotFoundException notInjected) {
                throw new InjectionException("Unable to inject bridge into Velocity's classloader", error);
            }
        }
    }

    private static AgentBuilder createAgentBuilder(
            final ClassLoader velocityLoader,
            final TransformationAudit audit) {
        final ElementMatcher.Junction<MethodDescription> showMethod = isMethod()
                .and(named("showDialog"))
                .and(takesArgument(0, named(DIALOG_LIKE)))
                .and(returns(void.class));
        final ElementMatcher.Junction<MethodDescription> closeMethod = isMethod()
                .and(named("closeDialog"))
                .and(takesArguments(0))
                .and(returns(void.class));

        return new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Compound(
                        audit,
                        AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly()))
                .type(named(AUDIENCE).or(named(Player.class.getName())), is(velocityLoader))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(PlayerShowAdvice.class).on(showMethod))
                        .visit(Advice.to(PlayerCloseAdvice.class).on(closeMethod)))
                .type(named(CONFIG_PACKET), is(velocityLoader))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(ConfigurationClickAdvice.class).on(isMethod()
                                .and(named("handle"))
                                .and(takesArguments(1))
                                .and(returns(boolean.class)))))
                .type(named(PLAY_HANDLER), is(velocityLoader))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder.visit(
                        Advice.to(PlayClickAdvice.class).on(isMethod()
                                .and(named("handleUnknown"))
                                .and(takesArgument(0, named(BYTE_BUF)))
                                .and(returns(void.class)))));
    }

    private static void verifyTransformed(
            final Instrumentation instrumentation,
            final RuntimeTypes types,
            final TransformationAudit audit) {
        try {
            instrumentation.retransformClasses(
                    types.audience(), Player.class, types.configPacket(), types.play());
        } catch (final Exception error) {
            throw new InjectionException("Velocity classes failed an explicit retransformation", error);
        }
        audit.verify();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!reset) {
            final boolean didReset;
            try {
                didReset = transformer.reset(instrumentation,
                        AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);
            } catch (final RuntimeException | LinkageError error) {
                throw new InjectionException("Unable to reset Velocity dialog transformations", error);
            }
            if (!didReset) {
                throw new InjectionException("Byte Buddy did not reset Velocity dialog transformations");
            }
            reset = true;
        }

        try {
            clearBridge.invoke(null);
        } catch (final ReflectiveOperationException error) {
            throw new InjectionException("Unable to clear the Velocity classloader bridge", error);
        }
        closed = true;
    }

    private record RuntimeTypes(
            ClassLoader loader,
            Class<?> audience,
            Class<?> configPacket,
            Class<?> play) {
    }
}
