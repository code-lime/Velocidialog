package org.lime.velocidialog.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.FloatBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
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

class DialogNbtCodecTest {
    @Test
    void preservesRawDialogsWithoutCopying() {
        final CompoundBinaryTag raw = CompoundBinaryTag.builder().putString("custom", "value").build();
        assertSame(raw, new DialogNbtCodec().encode(Dialog.raw(raw)));
    }

    @Test
    void encodesDefaultNoticeWithCodecDefaultsOmitted() {
        final CompoundBinaryTag encoded = new DialogNbtCodec().encode(dialog(
                DialogBase.builder(Component.text("Title")).build(),
                DialogType.notice()
        ));

        assertEquals(StringBinaryTag.stringBinaryTag("Title"), encoded.get("title"));
        assertEquals("minecraft:notice", encoded.getString("type"));
        assertNull(encoded.get("action"));
        assertNull(encoded.get("pause"));
        assertNull(encoded.get("can_close_with_escape"));
        assertNull(encoded.get("after_action"));
        assertNull(encoded.get("body"));
        assertNull(encoded.get("inputs"));
    }

    @Test
    void encodesEveryDialogLayout() {
        final ActionButton yes = ActionButton.builder(Component.text("Yes")).build();
        final ActionButton no = ActionButton.builder(Component.text("No")).build();
        final CompoundBinaryTag confirmation = new DialogNbtCodec().encode(simple(
                DialogType.confirmation(yes, no)
        ));
        assertEquals("minecraft:confirmation", confirmation.getString("type"));
        assertEquals("Yes", confirmation.getCompound("yes").getString("label"));
        assertEquals("No", confirmation.getCompound("no").getString("label"));

        final ActionButton exit = ActionButton.builder(Component.text("Exit")).width(90).build();
        final CompoundBinaryTag multi = new DialogNbtCodec().encode(simple(
                DialogType.multiAction(List.of(yes, no), exit, 3)
        ));
        assertEquals("minecraft:multi_action", multi.getString("type"));
        assertEquals(2, multi.getList("actions").size());
        assertEquals(3, multi.getInt("columns"));
        assertEquals("Exit", multi.getCompound("exit_action").getString("label"));

        final Dialog nested = simple(DialogType.notice());
        final CompoundBinaryTag list = new DialogNbtCodec().encode(simple(
                DialogType.dialogList(
                        RegistrySet.valueSet(RegistryKey.DIALOG, List.of(nested)),
                        exit,
                        4,
                        180
                )
        ));
        assertEquals("minecraft:dialog_list", list.getString("type"));
        assertEquals("minecraft:notice", compoundAt(list.getList("dialogs"), 0).getString("type"));
        assertEquals(4, list.getInt("columns"));
        assertEquals(180, list.getInt("button_width"));

        final CompoundBinaryTag links = new DialogNbtCodec().encode(simple(
                DialogType.serverLinks(exit, 5, 200)
        ));
        assertEquals("minecraft:server_links", links.getString("type"));
        assertEquals(5, links.getInt("columns"));
        assertEquals(200, links.getInt("button_width"));
    }

    @Test
    void encodesBodiesInputsAndNonDefaultBaseProperties() {
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

        final CompoundBinaryTag encoded = new DialogNbtCodec().encode(dialog(base, DialogType.notice()));
        assertEquals("External", encoded.getString("external_title"));
        assertEquals((byte) 0, encoded.getByte("can_close_with_escape"));
        assertEquals((byte) 0, encoded.getByte("pause"));
        assertEquals("wait_for_response", encoded.getString("after_action"));

        final ListBinaryTag bodies = encoded.getList("body");
        assertEquals(2, bodies.size());
        final CompoundBinaryTag message = compoundAt(bodies, 0);
        assertEquals("minecraft:plain_message", message.getString("type"));
        assertEquals("Message", message.getString("contents"));
        assertEquals(240, message.getInt("width"));
        final CompoundBinaryTag itemBody = compoundAt(bodies, 1);
        assertEquals("minecraft:item", itemBody.getString("type"));
        assertEquals("minecraft:stone", itemBody.getCompound("item").getString("id"));
        assertEquals(2, itemBody.getCompound("item").getInt("count"));
        assertEquals("Description", itemBody.getCompound("description").getString("contents"));
        assertEquals(220, itemBody.getCompound("description").getInt("width"));
        assertEquals((byte) 0, itemBody.getByte("show_decorations"));
        assertEquals((byte) 0, itemBody.getByte("show_tooltip"));
        assertEquals(32, itemBody.getInt("width"));
        assertEquals(48, itemBody.getInt("height"));

        final ListBinaryTag inputs = encoded.getList("inputs");
        assertEquals(4, inputs.size());
        final CompoundBinaryTag bool = compoundAt(inputs, 0);
        assertEquals("minecraft:boolean", bool.getString("type"));
        assertEquals((byte) 1, bool.getByte("initial"));
        assertEquals("yes", bool.getString("on_true"));
        assertEquals("no", bool.getString("on_false"));
        final CompoundBinaryTag range = compoundAt(inputs, 1);
        assertEquals("minecraft:number_range", range.getString("type"));
        assertEquals(FloatBinaryTag.floatBinaryTag(1.5f), range.get("start"));
        assertEquals(FloatBinaryTag.floatBinaryTag(0.5f), range.get("step"));
        final CompoundBinaryTag single = compoundAt(inputs, 2);
        assertEquals("minecraft:single_option", single.getString("type"));
        assertEquals(2, single.getList("options").size());
        assertEquals((byte) 0, single.getByte("label_visible"));
        final CompoundBinaryTag text = compoundAt(inputs, 3);
        assertEquals("minecraft:text", text.getString("type"));
        assertEquals(3, text.getCompound("multiline").getInt("max_lines"));
        assertEquals(60, text.getCompound("multiline").getInt("height"));
    }

    @Test
    void encodesAllActionKindsAndCustomHolderPayload() {
        final CompoundBinaryTag additionsTag = CompoundBinaryTag.builder()
                .putString("fixed", "field")
                .build();
        final BinaryTagHolder additions = DialogInternals.encodeCompound(additionsTag);
        final List<ActionButton> buttons = List.of(
                button("static", DialogAction.staticAction(ClickEvent.runCommand("/say hello"))),
                button("template", DialogAction.commandTemplate("/give @s $(item)")),
                button("custom", DialogAction.customClick(Key.key("example:submit"), additions))
        );

        final CompoundBinaryTag encoded = new DialogNbtCodec().encode(
                simple(DialogType.multiAction(buttons, null, 2))
        );
        final ListBinaryTag actions = encoded.getList("actions");
        final CompoundBinaryTag staticAction = compoundAt(actions, 0).getCompound("action");
        assertEquals("minecraft:run_command", staticAction.getString("type"));
        assertEquals("/say hello", staticAction.getString("command"));
        final CompoundBinaryTag template = compoundAt(actions, 1).getCompound("action");
        assertEquals("minecraft:dynamic/run_command", template.getString("type"));
        assertEquals("/give @s $(item)", template.getString("template"));
        final CompoundBinaryTag custom = compoundAt(actions, 2).getCompound("action");
        assertEquals("minecraft:dynamic/custom", custom.getString("type"));
        assertEquals("example:submit", custom.getString("id"));
        assertEquals(additionsTag, custom.getCompound("additions"));
    }

    @Test
    void aSingleBodyUsesTheCompactElementForm() {
        final DialogBase base = DialogBase.builder(Component.text("Single"))
                .body(List.of(DialogBody.plainMessage(Component.text("Only"))))
                .build();
        final CompoundBinaryTag encoded = new DialogNbtCodec().encode(dialog(base, DialogType.notice()));
        assertInstanceOf(CompoundBinaryTag.class, encoded.get("body"));
    }

    private static ActionButton button(final String label, final DialogAction action) {
        return ActionButton.builder(Component.text(label)).action(action).build();
    }

    private static Dialog simple(final org.lime.velocidialog.api.dialog.type.DialogType type) {
        return dialog(DialogBase.builder(Component.text("Title")).build(), type);
    }

    private static Dialog dialog(
            final DialogBase base,
            final org.lime.velocidialog.api.dialog.type.DialogType type
    ) {
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    private static CompoundBinaryTag compoundAt(final ListBinaryTag list, final int index) {
        return assertInstanceOf(CompoundBinaryTag.class, list.get(index));
    }
}
