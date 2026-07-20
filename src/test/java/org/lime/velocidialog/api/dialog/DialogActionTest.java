package org.lime.velocidialog.api.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.action.DialogActionCallback;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;

class DialogActionTest {
    @Test
    void createsStaticAndCommandTemplateActions() {
        final ClickEvent runCommand = ClickEvent.runCommand("/say hello");
        final DialogAction.StaticAction staticAction = DialogAction.staticAction(runCommand);
        assertSame(runCommand, staticAction.value());

        final Dialog dialog = Dialog.raw(CompoundBinaryTag.empty());
        final DialogAction.StaticAction showDialog = DialogAction.staticAction(ClickEvent.showDialog(dialog));
        assertEquals("show_dialog", showDialog.value().action().toString());

        assertThrows(
            IllegalArgumentException.class,
            () -> DialogAction.staticAction(ClickEvent.openFile("local.txt"))
        );

        final DialogAction.CommandTemplateAction template = DialogAction.commandTemplate("say $(message)");
        assertEquals("say $(message)", template.template());
        assertEquals("say $()", DialogAction.commandTemplate("say $()").template());
        assertEquals(
            "say $(\u043a\u043b\u044e\u0447)",
            DialogAction.commandTemplate("say $(\u043a\u043b\u044e\u0447)").template()
        );
        assertThrows(IllegalArgumentException.class, () -> DialogAction.commandTemplate("say hello"));
        assertThrows(IllegalArgumentException.class, () -> DialogAction.commandTemplate("say $(invalid-key)"));
        assertThrows(IllegalArgumentException.class, () -> DialogAction.commandTemplate("say $(unclosed"));
    }

    @Test
    void createsCustomClickWithCompoundAdditions() {
        final Key id = Key.key("example", "submit");
        final CompoundBinaryTag additions = CompoundBinaryTag.builder()
            .putString("context", "settings")
            .build();
        final BinaryTagHolder holder = DialogInternals.encodeCompound(additions);
        final DialogAction.CustomClickAction action = DialogAction.customClick(id, holder);

        assertSame(id, action.id());
        assertSame(holder, action.additions());
        assertEquals(additions, DialogInternals.decodeCompound(action.additions()));
        assertNull(DialogAction.customClick(id, null).additions());
        assertThrows(
            IllegalArgumentException.class,
            () -> DialogAction.customClick(id, BinaryTagHolder.binaryTagHolder("1"))
        );
    }

    @Test
    void callbackActionDelegatesPaperOptionsToInstalledRegistrar() {
        final AtomicBoolean invoked = new AtomicBoolean();
        final DialogActionCallback callback = (response, audience) -> invoked.set(true);
        final AtomicReference<DialogActionCallback> registeredCallback = new AtomicReference<>();
        final AtomicReference<ClickCallback.Options> registeredOptions = new AtomicReference<>();
        final Key callbackId = Key.key("velocidialog", "test_callback");
        final DialogInternals.CallbackRegistrar registrar = (registered, options) -> {
            registeredCallback.set(registered);
            registeredOptions.set(options);
            return DialogAction.customClick(callbackId, null);
        };

        final ClickCallback.Options defaults = ClickCallback.Options.builder().build();
        assertEquals(1, defaults.uses());
        assertEquals(ClickCallback.DEFAULT_LIFETIME, defaults.lifetime());

        final ClickCallback.Options options = ClickCallback.Options.builder()
            .uses(3)
            .lifetime(Duration.ofMinutes(7))
            .build();
        DialogInternals.installCallbackRegistrar(registrar);
        try {
            final DialogAction.CustomClickAction action = DialogAction.customClick(callback, options);
            assertSame(callback, registeredCallback.get());
            assertSame(options, registeredOptions.get());
            assertEquals(callbackId, action.id());
            assertFalse(invoked.get());
        } finally {
            DialogInternals.clearCallbackRegistrar(registrar);
        }

        assertThrows(
            IllegalStateException.class,
            () -> DialogAction.customClick(callback, options)
        );
    }

    @Test
    void responseViewReturnsOnlyCompatibleTypes() {
        final CompoundBinaryTag payload = CompoundBinaryTag.builder()
            .putString("text", "hello")
            .putBoolean("boolean", true)
            .putInt("intBoolean", 256)
            .putFloat("floatBoolean", -1.0F)
            .putFloat("number", 1.5F)
            .putString("wrong", "not a number")
            .build();
        final DialogResponseView response = DialogInternals.responseView(payload);

        assertEquals(payload, DialogInternals.decodeCompound(response.payload()));
        assertEquals("hello", response.getText("text"));
        assertEquals(Boolean.TRUE, response.getBoolean("boolean"));
        assertEquals(Boolean.FALSE, response.getBoolean("intBoolean"));
        assertEquals(Boolean.TRUE, response.getBoolean("floatBoolean"));
        assertEquals(1.5F, response.getFloat("number"));
        assertNull(response.getFloat("wrong"));
        assertNull(response.getText("missing"));
    }
}
