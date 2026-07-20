package org.lime.velocidialog.inject;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.proxy.VelocidialogBridge;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdviceBehaviorTest {
    @AfterEach
    void clearBridge() {
        VelocidialogBridge.clear();
    }

    @Test
    void playerAdviceLeavesNonPlayerAudienceBodiesUntouched() throws Exception {
        final AtomicReference<Object> shownPlayer = new AtomicReference<>();
        final AtomicReference<Object> shownDialog = new AtomicReference<>();
        final AtomicReference<Object> closedPlayer = new AtomicReference<>();
        VelocidialogBridge.install(
                (player, dialog) -> {
                    shownPlayer.set(player);
                    shownDialog.set(dialog);
                },
                closedPlayer::set,
                (player, packet) -> false,
                (player, buffer) -> false);

        final Class<?> transformed = new ByteBuddy()
                .redefine(DialogFixture.class)
                .visit(Advice.to(PlayerShowAdvice.class).on(named("showDialog")))
                .visit(Advice.to(PlayerCloseAdvice.class).on(named("closeDialog")))
                .make()
                .load(DialogFixture.class.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();
        final Object fixture = transformed.getConstructor().newInstance();
        final Object dialog = new Object();

        transformed.getMethod("showDialog", Object.class).invoke(fixture, dialog);
        transformed.getMethod("closeDialog").invoke(fixture);

        assertNull(shownPlayer.get());
        assertNull(shownDialog.get());
        assertNull(closedPlayer.get());
        assertEquals(1, transformed.getField("originalShows").getInt(fixture));
        assertEquals(1, transformed.getField("originalCloses").getInt(fixture));
    }

    @Test
    void configurationAdviceSkipsConsumedPacketsAndForcesHandledReturn() throws Exception {
        final AtomicBoolean consume = new AtomicBoolean(true);
        final AtomicReference<Object> interceptedPlayer = new AtomicReference<>();
        final AtomicReference<Object> interceptedPacket = new AtomicReference<>();
        VelocidialogBridge.install(
                (player, dialog) -> { },
                player -> { },
                (player, packet) -> {
                    interceptedPlayer.set(player);
                    interceptedPacket.set(packet);
                    return consume.get();
                },
                (player, buffer) -> false);

        final Class<?> transformed = transform(
                ConfigurationPacketFixture.class, ConfigurationClickAdvice.class, "handle");
        final Object player = new Object();
        final Object fixture = transformed.getConstructor().newInstance();
        final Method handle = transformed.getMethod("handle", Object.class);
        final Object sessionHandler = new ConfigurationFixture(player);

        assertTrue((boolean) handle.invoke(fixture, sessionHandler));
        assertSame(sessionHandler, interceptedPlayer.get());
        assertSame(fixture, interceptedPacket.get());
        assertEquals(0, transformed.getField("originalCalls").getInt(fixture));

        consume.set(false);
        assertFalse((boolean) handle.invoke(fixture, sessionHandler));
        assertEquals(1, transformed.getField("originalCalls").getInt(fixture));

        transformed.getField("originalResult").setBoolean(fixture, true);
        assertTrue((boolean) handle.invoke(fixture, sessionHandler));
        assertEquals(2, transformed.getField("originalCalls").getInt(fixture));
    }

    @Test
    void playAdviceSkipsOnlyConsumedPackets() throws Exception {
        final AtomicBoolean consume = new AtomicBoolean(true);
        final AtomicReference<Object> interceptedPlayer = new AtomicReference<>();
        final AtomicReference<Object> interceptedBuffer = new AtomicReference<>();
        VelocidialogBridge.install(
                (player, dialog) -> { },
                player -> { },
                (player, packet) -> false,
                (player, buffer) -> {
                    interceptedPlayer.set(player);
                    interceptedBuffer.set(buffer);
                    return consume.get();
                });

        final Class<?> transformed = transform(PlayFixture.class, PlayClickAdvice.class, "handleUnknown");
        final Object player = new Object();
        final Object fixture = transformed.getConstructor(Object.class).newInstance(player);
        final Method handle = transformed.getMethod("handleUnknown", Object.class);
        final Object buffer = new Object();

        handle.invoke(fixture, buffer);
        assertSame(player, interceptedPlayer.get());
        assertSame(buffer, interceptedBuffer.get());
        assertEquals(0, transformed.getField("originalCalls").getInt(fixture));

        consume.set(false);
        handle.invoke(fixture, buffer);
        assertEquals(1, transformed.getField("originalCalls").getInt(fixture));
    }

    private static Class<?> transform(
            final Class<?> fixture, final Class<?> advice, final String method) {
        return new ByteBuddy()
                .redefine(fixture)
                .visit(Advice.to(advice).on(named(method)))
                .make()
                .load(fixture.getClassLoader(), ClassLoadingStrategy.Default.CHILD_FIRST)
                .getLoaded();
    }

    public static final class DialogFixture {
        public int originalShows;
        public int originalCloses;

        public void showDialog(final Object dialog) {
            originalShows++;
        }

        public void closeDialog() {
            originalCloses++;
        }
    }

    public static final class ConfigurationFixture {
        public final Object player;

        public ConfigurationFixture(final Object player) {
            this.player = player;
        }
    }

    public static final class ConfigurationPacketFixture {
        public int originalCalls;
        public boolean originalResult;

        public boolean handle(final Object sessionHandler) {
            originalCalls++;
            return originalResult;
        }
    }

    public static final class PlayFixture {
        public final Object player;
        public int originalCalls;

        public PlayFixture(final Object player) {
            this.player = player;
        }

        public void handleUnknown(final Object buffer) {
            originalCalls++;
        }
    }
}
