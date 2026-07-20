package org.lime.velocidialog.api.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.DialogBase.DialogAfterAction;
import org.lime.velocidialog.api.dialog.body.DialogBody;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.dialog.type.DialogType;

class DialogModelTest {
    @Test
    void createsModeledAndRawDialogsWithExclusiveRepresentations() {
        final DialogBase base = DialogBase.builder(Component.text("Title")).build();
        final Dialog modeled = Dialog.create(factory -> factory.empty()
            .base(base)
            .type(DialogType.notice()));

        assertSame(base, DialogInternals.entry(modeled).base());
        assertNull(DialogInternals.rawData(modeled));

        final CompoundBinaryTag tag = CompoundBinaryTag.builder()
            .putString("type", "minecraft:notice")
            .build();
        final Dialog raw = Dialog.raw(tag);

        assertSame(tag, DialogInternals.rawData(raw));
        assertNull(DialogInternals.entry(raw));
    }

    @Test
    void registryFactoryRequiresOneCompleteBuilder() {
        assertThrows(IllegalStateException.class, () -> Dialog.create(factory -> {
        }));
        assertThrows(
            IllegalStateException.class,
            () -> Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Title")).build()))
        );
        assertThrows(
            IllegalStateException.class,
            () -> Dialog.create(factory -> {
                factory.empty();
                factory.empty();
            })
        );
    }

    @Test
    void baseAndButtonUsePaperDefaults() {
        final DialogBase base = DialogBase.builder(Component.text("Title")).build();
        assertNull(base.externalTitle());
        assertTrue(base.canCloseWithEscape());
        assertTrue(base.pause());
        assertEquals(DialogAfterAction.CLOSE, base.afterAction());
        assertEquals("close", DialogAfterAction.NAMES.keyOrThrow(base.afterAction()));
        assertEquals(List.of(), base.body());
        assertEquals(List.of(), base.inputs());

        final ActionButton button = ActionButton.builder(Component.text("OK")).build();
        assertEquals(150, button.width());
        assertNull(button.tooltip());
        assertNull(button.action());
    }

    @Test
    void baseCopiesCollectionsAndRejectsPauseWithNone() {
        final List<PlainMessageDialogBody> mutableBody = new ArrayList<>();
        mutableBody.add(DialogBody.plainMessage(Component.text("first")));
        final DialogBase base = DialogBase.builder(Component.text("Title"))
            .body(mutableBody)
            .build();

        mutableBody.add(DialogBody.plainMessage(Component.text("second")));
        assertEquals(1, base.body().size());
        assertThrows(UnsupportedOperationException.class, () -> base.body().clear());

        assertThrows(
            IllegalArgumentException.class,
            () -> DialogBase.builder(Component.text("Title"))
                .afterAction(DialogAfterAction.NONE)
                .build()
        );

        final DialogBase nonPausing = DialogBase.builder(Component.text("Title"))
            .pause(false)
            .afterAction(DialogAfterAction.NONE)
            .build();
        assertEquals(DialogAfterAction.NONE, nonPausing.afterAction());
    }

    @Test
    void validatesButtonWidth() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ActionButton.builder(Component.text("bad")).width(0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> ActionButton.builder(Component.text("bad")).width(1025)
        );
    }
}
