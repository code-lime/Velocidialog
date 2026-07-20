package org.lime.velocidialog.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.velocitypowered.api.proxy.Player;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.DataComponentValue;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.object.ObjectContents;
import org.junit.jupiter.api.Test;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.dialog.input.DialogInput;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.lime.velocidialog.codec.DialogNbtCodec;
import org.lime.velocidialog.codec.MinecraftComponentNbtEncoder;
import org.lime.velocidialog.codec.MinecraftItemNbtEncoder;
import org.lime.velocidialog.protocol.NbtWireCodec;

class Adventure5CompatibilityTest {
    @Test
    void modelAndNestedShowDialogLinkAgainstAdventure5() {
        final Dialog nested = notice("Nested", null);
        final Dialog root = notice("Root", DialogAction.staticAction(ClickEvent.showDialog(nested)));

        final CompoundBinaryTag encoded = new DialogNbtCodec().encode(root);
        final CompoundBinaryTag action = encoded.getCompound("action")
                .getCompound("action");

        assertEquals("minecraft:show_dialog", action.getString("type"));
        assertEquals("Nested", action.getCompound("dialog").getString("title"));
    }

    @Test
    void velocity4RetainsExactUnsupportedMethodDescriptors() throws Exception {
        assertEquals(void.class,
                Player.class.getMethod("showDialog", DialogLike.class).getReturnType());
        assertEquals(void.class, Player.class.getMethod("closeDialog").getReturnType());
        assertNotNull(Class.forName("org.lime.velocidialog.inject.VelocityInjector"));
    }

    @Test
    void adventureFiveClickCallbackOptionsRetainTheirBinaryContract() {
        final ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(4)
                .lifetime(Duration.ofSeconds(45))
                .build();
        final AtomicReference<ClickCallback.Options> received = new AtomicReference<>();
        final DialogInternals.CallbackRegistrar registrar = (callback, suppliedOptions) -> {
            received.set(suppliedOptions);
            return DialogAction.customClick(Key.key("velocidialog:compatibility"), null);
        };
        DialogInternals.installCallbackRegistrar(registrar);
        try {
            final DialogAction.CustomClickAction action = DialogAction.customClick(
                    (response, audience) -> { }, options);
            assertEquals("velocidialog:compatibility", action.id().asString());
            assertEquals(4, received.get().uses());
            assertEquals(Duration.ofSeconds(45), received.get().lifetime());
        } finally {
            DialogInternals.clearCallbackRegistrar(registrar);
        }
    }

    @Test
    void rawDialogIsPreservedOnAdventure5() {
        final CompoundBinaryTag raw = CompoundBinaryTag.builder()
                .putString("type", "minecraft:notice")
                .putString("title", "raw")
                .build();
        assertSame(raw, new DialogNbtCodec().encode(Dialog.raw(raw)));
    }

    @Test
    void adventureFiveObjectFallbackIsPreservedWithoutStaticLinkage() {
        final Component object = Component.object(ObjectContents.sprite(
                net.kyori.adventure.key.Key.key("minecraft:blocks"),
                net.kyori.adventure.key.Key.key("minecraft:stone")
        )).fallback(Component.text("Stone"));
        final CompoundBinaryTag encoded = (CompoundBinaryTag) new MinecraftComponentNbtEncoder(
                ignored -> {
                    throw new AssertionError("Unexpected nested dialog");
                }
        ).encode(object);

        assertEquals("Stone", encoded.getString("fallback"));
    }

    @Test
    void namelessNetworkNbtRoundTripsOnAdventureFive() {
        final CompoundBinaryTag expected = CompoundBinaryTag.builder()
                .putString("answer", "velocity4")
                .putInt("number", 5)
                .build();
        final ByteBuf encoded = Unpooled.buffer();
        try {
            NbtWireCodec.write(encoded, expected);
            assertEquals(expected, NbtWireCodec.readCompound(encoded));
            assertEquals(0, encoded.readableBytes());
        } finally {
            encoded.release();
        }
    }

    @Test
    void adventureFiveShowItemDataComponentsUseTheMojangShape() {
        final DataComponentValue.TagSerializable customData = () ->
                BinaryTagHolder.binaryTagHolder("{source:\"adventure5\"}");
        final CompoundBinaryTag encoded = new MinecraftItemNbtEncoder().encode(
                HoverEvent.ShowItem.showItem(
                        net.kyori.adventure.key.Key.key("minecraft:stone"),
                        2,
                        Map.of(net.kyori.adventure.key.Key.key("minecraft:custom_data"), customData)
                )
        );

        assertEquals("minecraft:stone", encoded.getString("id"));
        assertEquals(2, encoded.getInt("count"));
        assertEquals("adventure5", encoded.getCompound("components")
                .getCompound("minecraft:custom_data").getString("source"));
    }

    @Test
    void adventureFiveCustomClickNbtPayloadUsesTheMojangShape() {
        final Component component = Component.text("Custom").clickEvent(ClickEvent.custom(
                Key.key("velocidialog:test"),
                BinaryTagHolder.binaryTagHolder("{answer:42,source:\"adventure5\"}")
        ));
        final CompoundBinaryTag encoded = (CompoundBinaryTag) new MinecraftComponentNbtEncoder(
                ignored -> {
                    throw new AssertionError("Unexpected nested dialog");
                }
        ).encode(component);
        final CompoundBinaryTag click = encoded.getCompound("click_event");

        assertEquals("custom", click.getString("action"));
        assertEquals("velocidialog:test", click.getString("id"));
        assertEquals(42, click.getCompound("payload").getInt("answer"));
        assertEquals("adventure5", click.getCompound("payload").getString("source"));
    }

    private static Dialog notice(final String title, final DialogAction action) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title))
                        .inputs(List.of(DialogInput.bool("accepted", Component.text("Accept")).build()))
                        .build())
                .type(DialogType.notice(ActionButton.builder(Component.text("OK"))
                        .action(action)
                        .build())));
    }
}
