package org.lime.velocidialog.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.body.DialogBody;
import org.lime.velocidialog.api.dialog.input.DialogInput;
import org.lime.velocidialog.api.dialog.input.SingleOptionDialogInput;
import org.lime.velocidialog.api.dialog.input.TextDialogInput;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.lime.velocidialog.api.registry.RegistryKey;
import org.lime.velocidialog.api.registry.set.RegistrySet;

/**
 * Frozen compatibility vectors emitted by Mojang's 1.21.6 {@code Dialog.DIRECT_CODEC}.
 * See the fixture README for the exact provenance and regeneration procedure.
 */
class DialogDirectCodecGoldenTest {
    private static final String FIXTURE_ROOT = "/fixtures/mojang-dialog-direct-codec-1.21.6/";

    @Test
    void defaultNoticeMatchesDirectCodec() throws IOException {
        assertFixture("notice-default", simple("Title", DialogType.notice()));
    }

    @Test
    void comprehensiveMultiActionMatchesDirectCodec() throws IOException {
        final HoverEvent.ShowItem item = HoverEvent.ShowItem.showItem(Key.key("minecraft:stone"), 2);
        final DialogBase base = DialogBase.builder(Component.text("Form"))
                .externalTitle(Component.text("External"))
                .canCloseWithEscape(false)
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(
                        DialogBody.plainMessage(Component.text("Message"), 240),
                        DialogBody.item(item)
                                .description(DialogBody.plainMessage(Component.text("Description"), 220))
                                .showDecorations(false)
                                .showTooltip(false)
                                .width(32)
                                .height(48)
                                .build()
                ))
                .inputs(List.of(
                        DialogInput.bool("enabled", Component.text("Enabled"), true, "yes", "no"),
                        DialogInput.numberRange(
                                "amount", 210, Component.text("Amount"), "example.format",
                                1.5f, 9.5f, 4.5f, 0.5f
                        ),
                        DialogInput.singleOption(
                                "choice", 220,
                                List.of(
                                        SingleOptionDialogInput.OptionEntry.create("one", null, false),
                                        SingleOptionDialogInput.OptionEntry.create(
                                                "two", Component.text("Second"), true
                                        )
                                ),
                                Component.text("Choice"), false
                        ),
                        DialogInput.text(
                                "comment", 230, Component.text("Comment"), false, "initial", 80,
                                TextDialogInput.MultilineOptions.create(3, 60)
                        )
                ))
                .build();
        final CompoundBinaryTag additionsTag = CompoundBinaryTag.builder()
                .putString("fixed", "field")
                .build();
        final BinaryTagHolder additions = DialogInternals.encodeCompound(additionsTag);
        final List<ActionButton> actions = List.of(
                ActionButton.builder(Component.text("Run"))
                        .tooltip(Component.text("Run it"))
                        .width(120)
                        .action(DialogAction.staticAction(ClickEvent.runCommand("/say hello")))
                        .build(),
                ActionButton.builder(Component.text("Template"))
                        .action(DialogAction.commandTemplate("/give @s $(item)"))
                        .build(),
                ActionButton.builder(Component.text("Custom"))
                        .action(DialogAction.customClick(Key.key("example:submit"), additions))
                        .build()
        );
        final ActionButton exit = ActionButton.builder(Component.text("Exit")).width(90).build();

        assertFixture(
                "multi-action-comprehensive",
                dialog(base, DialogType.multiAction(actions, exit, 3))
        );
    }

    @Test
    void nestedShowDialogAndConfirmationMatchDirectCodec() throws IOException {
        final Dialog nested = simple("Nested", DialogType.notice());
        final ActionButton yes = ActionButton.builder(Component.text("Yes"))
                .action(DialogAction.staticAction(ClickEvent.showDialog(nested)))
                .build();
        final ActionButton no = ActionButton.builder(Component.text("No"))
                .action(DialogAction.staticAction(ClickEvent.openUrl("https://example.com")))
                .build();

        assertFixture(
                "confirmation-nested-show-dialog",
                simple("Confirm", DialogType.confirmation(yes, no))
        );
    }

    @Test
    void inlineDialogListMatchesDirectCodec() throws IOException {
        final Dialog first = simple("First", DialogType.notice());
        final Dialog second = simple(
                "Second",
                DialogType.confirmation(button("Yes"), button("No"))
        );
        final ActionButton back = ActionButton.builder(Component.text("Back")).width(90).build();

        assertFixture(
                "dialog-list-inline",
                simple(
                        "List",
                        DialogType.dialogList(
                                RegistrySet.valueSet(RegistryKey.DIALOG, List.of(first, second)),
                                back,
                                4,
                                180
                        )
                )
        );
    }

    @Test
    void serverLinksMatchesDirectCodec() throws IOException {
        assertFixture(
                "server-links",
                simple("Links", DialogType.serverLinks(button("Done"), 5, 200))
        );
    }

    @Test
    void compactSingleBodyMatchesDirectCodec() throws IOException {
        final DialogBase base = DialogBase.builder(Component.text("Single"))
                .body(List.of(DialogBody.plainMessage(Component.text("Only"))))
                .build();
        assertFixture("single-body-compact", dialog(base, DialogType.notice()));
    }

    private static void assertFixture(final String name, final Dialog dialog) throws IOException {
        assertEquals(readFixture(name), new DialogNbtCodec().encode(dialog), name);
    }

    private static CompoundBinaryTag readFixture(final String name) throws IOException {
        final String resource = FIXTURE_ROOT + name + ".snbt";
        try (InputStream input = DialogDirectCodecGoldenTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            final String snbt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            final BinaryTag tag = TagStringIO.tagStringIO().asTag(snbt);
            return assertInstanceOf(CompoundBinaryTag.class, tag, resource);
        }
    }

    private static ActionButton button(final String label) {
        return ActionButton.builder(Component.text(label)).build();
    }

    private static Dialog simple(final String title, final org.lime.velocidialog.api.dialog.type.DialogType type) {
        return dialog(DialogBase.builder(Component.text(title)).build(), type);
    }

    private static Dialog dialog(
            final DialogBase base,
            final org.lime.velocidialog.api.dialog.type.DialogType type
    ) {
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }
}
