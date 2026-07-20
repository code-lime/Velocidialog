package org.lime.velocidialog.api.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.dialog.type.ConfirmationType;
import org.lime.velocidialog.api.dialog.type.DialogListType;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.lime.velocidialog.api.dialog.type.MultiActionType;
import org.lime.velocidialog.api.dialog.type.NoticeType;
import org.lime.velocidialog.api.dialog.type.ServerLinksType;
import org.lime.velocidialog.api.registry.RegistryKey;
import org.lime.velocidialog.api.registry.set.RegistrySet;
import org.lime.velocidialog.api.registry.set.RegistryValueSet;

class DialogTypeTest {
    private static ActionButton button(final String label) {
        return ActionButton.builder(Component.text(label)).build();
    }

    private static Dialog noticeDialog(final String title) {
        return Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Component.text(title)).build())
            .type(DialogType.notice()));
    }

    private static RegistryValueSet<Dialog> dialogSet(final Iterable<? extends Dialog> dialogs) {
        return RegistrySet.valueSet(RegistryKey.DIALOG, dialogs);
    }

    @Test
    void createsConfirmationAndNotice() {
        final ActionButton yes = button("Yes");
        final ActionButton no = button("No");
        final ConfirmationType confirmation = DialogType.confirmation(yes, no);
        assertSame(yes, confirmation.yesButton());
        assertSame(no, confirmation.noButton());

        final NoticeType notice = DialogType.notice();
        assertEquals(Component.translatable("gui.ok"), notice.action().label());
        assertEquals(150, notice.action().width());

        final NoticeType explicitNotice = DialogType.notice(yes);
        assertSame(yes, explicitNotice.action());
    }

    @Test
    void multiActionUsesDefaultsAndCopiesActions() {
        final List<ActionButton> mutable = new ArrayList<>();
        mutable.add(button("One"));
        final MultiActionType type = DialogType.multiAction(mutable).build();

        mutable.add(button("Two"));
        assertEquals(1, type.actions().size());
        assertEquals(2, type.columns());
        assertNull(type.exitAction());
        assertThrows(UnsupportedOperationException.class, () -> type.actions().clear());

        final ActionButton exit = button("Exit");
        final MultiActionType configured = DialogType.multiAction(type.actions(), exit, 3);
        assertSame(exit, configured.exitAction());
        assertEquals(3, configured.columns());

        assertThrows(IllegalArgumentException.class, () -> DialogType.multiAction(List.of()));
        assertThrows(IllegalArgumentException.class, () -> DialogType.multiAction(type.actions()).columns(0));
    }

    @Test
    void dialogListUsesDefaultsAndAllowsEmptySet() {
        final Dialog first = noticeDialog("First");
        final List<Dialog> mutable = new ArrayList<>(List.of(first));
        final RegistryValueSet<Dialog> dialogs = dialogSet(mutable);
        final DialogListType type = DialogType.dialogList(dialogs).build();

        mutable.clear();
        assertSame(dialogs, type.dialogs());
        assertSame(RegistryKey.DIALOG, type.dialogs().registryKey());
        assertEquals(List.of(first), dialogs.values());
        assertEquals(2, type.columns());
        assertEquals(150, type.buttonWidth());
        assertNull(type.exitAction());

        final RegistryValueSet<Dialog> empty = dialogSet(List.of());
        assertEquals(0, DialogType.dialogList(empty).build().dialogs().size());

        final ActionButton exit = button("Exit");
        final DialogListType configured = DialogType.dialogList(dialogSet(List.of(first)), exit, 1, 300);
        assertSame(exit, configured.exitAction());
        assertEquals(1, configured.columns());
        assertEquals(300, configured.buttonWidth());

        assertThrows(IllegalArgumentException.class, () -> DialogType.dialogList(empty).columns(0));
        assertThrows(IllegalArgumentException.class, () -> DialogType.dialogList(empty).buttonWidth(1025));
    }

    @Test
    void dialogEntryBuildsInlineRegistryValueSets() {
        final Dialog listDialog = Dialog.create(factory -> {
            final DialogRegistryEntry.Builder root = factory.empty();
            final RegistryValueSet<Dialog> entries = root.registryValueSet()
                .add(nested -> nested.empty()
                    .base(DialogBase.builder(Component.text("Nested")).build())
                    .type(DialogType.notice()))
                .build();
            root.base(DialogBase.builder(Component.text("List")).build())
                .type(DialogType.dialogList(entries).build());
        });

        final DialogListType type = (DialogListType) DialogInternals.entry(listDialog).type();
        assertEquals(1, type.dialogs().size());
    }

    @Test
    void serverLinksValidatesItsRequiredDimensions() {
        final ServerLinksType type = DialogType.serverLinks(null, 3, 200);
        assertNull(type.exitAction());
        assertEquals(3, type.columns());
        assertEquals(200, type.buttonWidth());

        assertThrows(IllegalArgumentException.class, () -> DialogType.serverLinks(null, 0, 200));
        assertThrows(IllegalArgumentException.class, () -> DialogType.serverLinks(null, 1, 0));
    }
}
