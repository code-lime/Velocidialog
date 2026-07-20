package org.lime.velocidialog.api.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.body.DialogBody;
import org.lime.velocidialog.api.dialog.body.ItemDialogBody;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.input.BooleanDialogInput;
import org.lime.velocidialog.api.dialog.input.DialogInput;
import org.lime.velocidialog.api.dialog.input.NumberRangeDialogInput;
import org.lime.velocidialog.api.dialog.input.SingleOptionDialogInput;
import org.lime.velocidialog.api.dialog.input.TextDialogInput;

class DialogBodyInputTest {
    @Test
    @SuppressWarnings("deprecation")
    void bodyFactoriesUsePaperDefaultsAndBounds() {
        final PlainMessageDialogBody message = DialogBody.plainMessage(Component.text("Body"));
        assertEquals(200, message.width());
        assertThrows(
            IllegalArgumentException.class,
            () -> DialogBody.plainMessage(Component.text("bad"), 0)
        );

        final HoverEvent.ShowItem showItem = HoverEvent.ShowItem.of(Key.key("minecraft:stone"), 1);
        final ItemDialogBody item = DialogBody.item(showItem).build();
        assertSame(showItem, item.item());
        assertNull(item.description());
        assertTrue(item.showDecorations());
        assertTrue(item.showTooltip());
        assertEquals(16, item.width());
        assertEquals(16, item.height());

        final ItemDialogBody configured = DialogBody.item(showItem, message, false, false, 32, 24);
        assertSame(message, configured.description());
        assertFalse(configured.showDecorations());
        assertFalse(configured.showTooltip());
        assertEquals(32, configured.width());
        assertEquals(24, configured.height());

        assertThrows(IllegalArgumentException.class, () -> DialogBody.item(showItem).width(257));
        assertThrows(IllegalArgumentException.class, () -> DialogBody.item(showItem).height(0));
    }

    @Test
    void booleanInputUsesPaperDefaultsAndValidatesKey() {
        final BooleanDialogInput input = DialogInput.bool("enabled", Component.text("Enabled")).build();
        assertFalse(input.initial());
        assertEquals("true", input.onTrue());
        assertEquals("false", input.onFalse());

        final BooleanDialogInput configured = DialogInput.bool(
            "enabled",
            Component.text("Enabled"),
            true,
            "yes",
            "no"
        );
        assertTrue(configured.initial());
        assertEquals("yes", configured.onTrue());
        assertEquals("no", configured.onFalse());

        assertThrows(
            IllegalArgumentException.class,
            () -> DialogInput.bool("invalid-key", Component.text("Invalid"))
        );
        assertEquals("", DialogInput.bool("", Component.text("Empty")).build().key());
        assertEquals(
            "\u043a\u043b\u044e\u0447",
            DialogInput.bool("\u043a\u043b\u044e\u0447", Component.text("Unicode")).build().key()
        );
    }

    @Test
    void numberRangeUsesDefaultsAndValidatesOptionalNumbers() {
        final NumberRangeDialogInput input = DialogInput.numberRange(
            "volume",
            Component.text("Volume"),
            0.0F,
            10.0F
        ).build();

        assertEquals(200, input.width());
        assertEquals("options.generic_value", input.labelFormat());
        assertNull(input.initial());
        assertNull(input.step());

        final NumberRangeDialogInput configured = DialogInput.numberRange(
            "volume",
            300,
            Component.text("Volume"),
            "%s",
            0.0F,
            10.0F,
            5.0F,
            0.5F
        );
        assertEquals(300, configured.width());
        assertEquals("%s", configured.labelFormat());
        assertEquals(5.0F, configured.initial());
        assertEquals(0.5F, configured.step());

        assertThrows(
            IllegalArgumentException.class,
            () -> DialogInput.numberRange("volume", Component.text("Volume"), 0.0F, 10.0F).initial(11.0F)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DialogInput.numberRange("volume", Component.text("Volume"), 0.0F, 10.0F).step(0.0F)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DialogInput.numberRange("volume", Component.text("Volume"), 0.0F, 10.0F).step(Float.NaN)
        );
    }

    @Test
    void singleOptionRequiresEntriesAndCopiesThem() {
        final SingleOptionDialogInput.OptionEntry first = SingleOptionDialogInput.OptionEntry.create(
            "first",
            Component.text("First"),
            true
        );
        final List<SingleOptionDialogInput.OptionEntry> mutable = new ArrayList<>(List.of(first));
        final SingleOptionDialogInput input = DialogInput.singleOption(
            "choice",
            Component.text("Choice"),
            mutable
        ).build();

        mutable.clear();
        assertEquals(List.of(first), input.entries());
        assertEquals(200, input.width());
        assertTrue(input.labelVisible());

        final SingleOptionDialogInput configured = DialogInput.singleOption(
            "choice",
            300,
            List.of(first),
            Component.text("Choice"),
            false
        );
        assertEquals(300, configured.width());
        assertFalse(configured.labelVisible());

        assertThrows(
            IllegalArgumentException.class,
            () -> DialogInput.singleOption("choice", Component.text("Choice"), List.of())
        );
        final SingleOptionDialogInput.OptionEntry second = SingleOptionDialogInput.OptionEntry.create(
            "second",
            null,
            true
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DialogInput.singleOption("choice", Component.text("Choice"), List.of(first, second))
        );
    }

    @Test
    void textInputUsesDefaultsAndChecksLengthAndMultilineBounds() {
        final TextDialogInput input = DialogInput.text("name", Component.text("Name")).build();
        assertEquals(200, input.width());
        assertTrue(input.labelVisible());
        assertEquals("", input.initial());
        assertEquals(32, input.maxLength());
        assertNull(input.multiline());

        final TextDialogInput.MultilineOptions multiline = TextDialogInput.MultilineOptions.create(3, 100);
        final TextDialogInput multilineInput = DialogInput.text(
            "description",
            300,
            Component.text("Description"),
            false,
            "initial",
            64,
            multiline
        );
        assertSame(multiline, multilineInput.multiline());
        assertEquals(300, multilineInput.width());
        assertFalse(multilineInput.labelVisible());
        assertEquals("initial", multilineInput.initial());
        assertEquals(64, multilineInput.maxLength());

        assertThrows(
            IllegalStateException.class,
            () -> DialogInput.text("name", Component.text("Name")).initial("too long").maxLength(3).build()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TextDialogInput.MultilineOptions.create(0, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TextDialogInput.MultilineOptions.create(null, 513)
        );
    }
}
