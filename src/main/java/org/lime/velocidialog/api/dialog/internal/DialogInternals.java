package org.lime.velocidialog.api.dialog.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.NumberBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.event.ClickCallback;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.DialogRegistryEntry;
import org.lime.velocidialog.api.dialog.DialogResponseView;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.action.DialogActionCallback;
import org.lime.velocidialog.api.dialog.type.DialogType;
import org.lime.velocidialog.api.registry.RegistryBuilderFactory;
import org.lime.velocidialog.api.registry.RegistryKey;
import org.lime.velocidialog.api.registry.TypedKey;
import org.lime.velocidialog.api.registry.set.RegistrySet;
import org.lime.velocidialog.api.registry.set.RegistryValueSet;
import org.lime.velocidialog.api.registry.set.RegistryValueSetBuilder;

/** Internal construction and representation bridge for the Paper-shaped public API. */
@ApiStatus.Internal
public final class DialogInternals {
    private static final TagStringIO TAG_STRING_IO = TagStringIO.tagStringIO();
    private static volatile @Nullable CallbackRegistrar callbackRegistrar;

    private DialogInternals() {
    }

    public static Dialog create(
            final Consumer<RegistryBuilderFactory<Dialog, ? extends DialogRegistryEntry.Builder>> value) {
        Objects.requireNonNull(value, "value");
        final EntryBuilderFactory factory = new EntryBuilderFactory();
        value.accept(factory);
        return new ModeledDialog(factory.build());
    }

    public static Dialog raw(final CompoundBinaryTag data) {
        return new RawDialog(Objects.requireNonNull(data, "data"));
    }

    public static @Nullable DialogRegistryEntry entry(final Dialog dialog) {
        Objects.requireNonNull(dialog, "dialog");
        if (dialog instanceof ModeledDialog modeled) {
            return modeled.entry();
        }
        if (dialog instanceof RawDialog) {
            return null;
        }
        throw unsupportedDialog(dialog);
    }

    public static @Nullable CompoundBinaryTag rawData(final Dialog dialog) {
        Objects.requireNonNull(dialog, "dialog");
        if (dialog instanceof RawDialog raw) {
            return raw.data();
        }
        if (dialog instanceof ModeledDialog) {
            return null;
        }
        throw unsupportedDialog(dialog);
    }

    public static DialogResponseView responseView(final CompoundBinaryTag payload) {
        return new ResponseView(Objects.requireNonNull(payload, "payload"));
    }

    public static CompoundBinaryTag decodeCompound(final BinaryTagHolder holder) {
        Objects.requireNonNull(holder, "holder");
        try {
            return TAG_STRING_IO.asCompound(holder.string());
        } catch (final IOException | RuntimeException malformed) {
            throw new IllegalArgumentException("BinaryTagHolder must contain a valid compound tag", malformed);
        }
    }

    public static BinaryTagHolder encodeCompound(final CompoundBinaryTag tag) {
        Objects.requireNonNull(tag, "tag");
        try {
            return BinaryTagHolder.binaryTagHolder(TAG_STRING_IO.asString(tag));
        } catch (final IOException impossible) {
            throw new IllegalStateException("Unable to encode a compound tag", impossible);
        }
    }

    public static DialogAction.CustomClickAction registerCallback(
            final DialogActionCallback callback,
            final ClickCallback.Options options) {
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(options, "options");
        final CallbackRegistrar registrar = callbackRegistrar;
        if (registrar == null) {
            throw new IllegalStateException(
                    "Velocidialog is not initialized; dialog callbacks cannot be registered");
        }
        return Objects.requireNonNull(registrar.register(callback, options),
                "callback registrar returned null");
    }

    public static void installCallbackRegistrar(final CallbackRegistrar registrar) {
        Objects.requireNonNull(registrar, "registrar");
        synchronized (DialogInternals.class) {
            if (callbackRegistrar != null && callbackRegistrar != registrar) {
                throw new IllegalStateException("A Velocidialog callback registrar is already installed");
            }
            callbackRegistrar = registrar;
        }
    }

    public static void clearCallbackRegistrar(final CallbackRegistrar registrar) {
        synchronized (DialogInternals.class) {
            if (callbackRegistrar == registrar) {
                callbackRegistrar = null;
            }
        }
    }

    private static IllegalArgumentException unsupportedDialog(final Dialog dialog) {
        return new IllegalArgumentException(
                "Unsupported Dialog implementation " + dialog.getClass().getName());
    }

    @FunctionalInterface
    public interface CallbackRegistrar {
        DialogAction.CustomClickAction register(
                DialogActionCallback callback,
                ClickCallback.Options options);
    }

    private record ModeledDialog(DialogRegistryEntry entry) implements Dialog {
        private ModeledDialog {
            Objects.requireNonNull(entry, "entry");
        }
    }

    private record RawDialog(CompoundBinaryTag data) implements Dialog {
        private RawDialog {
            Objects.requireNonNull(data, "data");
        }
    }

    private record Entry(DialogBase base, DialogType type) implements DialogRegistryEntry {
        private Entry {
            Objects.requireNonNull(base, "base");
            Objects.requireNonNull(type, "type");
        }
    }

    private static final class EntryBuilder implements DialogRegistryEntry.Builder {
        private @Nullable DialogBase base;
        private @Nullable DialogType type;

        @Override
        public RegistryValueSetBuilder<Dialog, DialogRegistryEntry.Builder> registryValueSet() {
            return new DirectValueSetBuilder();
        }

        @Override
        public DialogBase base() {
            if (this.base == null) {
                throw new IllegalStateException("dialogBase has not been configured");
            }
            return this.base;
        }

        @Override
        public DialogType type() {
            if (this.type == null) {
                throw new IllegalStateException("dialogType has not been configured");
            }
            return this.type;
        }

        @Override
        public DialogRegistryEntry.Builder base(final DialogBase dialogBase) {
            this.base = Objects.requireNonNull(dialogBase, "dialogBase");
            return this;
        }

        @Override
        public DialogRegistryEntry.Builder type(final DialogType dialogType) {
            this.type = Objects.requireNonNull(dialogType, "dialogType");
            return this;
        }

        private DialogRegistryEntry build() {
            return new Entry(this.base(), this.type());
        }
    }

    private static final class EntryBuilderFactory
            implements RegistryBuilderFactory<Dialog, DialogRegistryEntry.Builder> {
        private @Nullable EntryBuilder builder;
        private boolean used;

        @Override
        public DialogRegistryEntry.Builder empty() {
            this.claim();
            this.builder = new EntryBuilder();
            return this.builder;
        }

        @Override
        public DialogRegistryEntry.Builder copyFrom(final TypedKey<Dialog> key) {
            Objects.requireNonNull(key, "key");
            this.claim();
            throw new UnsupportedOperationException(
                    "Velocity supports only anonymous inline dialogs; copyFrom is unavailable");
        }

        private void claim() {
            if (this.used) {
                throw new IllegalStateException(
                        "empty() or copyFrom() has already been called on this factory");
            }
            this.used = true;
        }

        private DialogRegistryEntry build() {
            if (this.builder == null) {
                throw new IllegalStateException("The dialog builder factory was not used");
            }
            return this.builder.build();
        }
    }

    private static final class DirectValueSetBuilder
            implements RegistryValueSetBuilder<Dialog, DialogRegistryEntry.Builder> {
        private final List<Dialog> dialogs = new ArrayList<>();

        @Override
        public RegistryValueSetBuilder<Dialog, DialogRegistryEntry.Builder> add(
                final Consumer<RegistryBuilderFactory<Dialog,
                        ? extends DialogRegistryEntry.Builder>> builder) {
            this.dialogs.add(DialogInternals.create(builder));
            return this;
        }

        @Override
        public RegistryValueSet<Dialog> build() {
            return RegistrySet.valueSet(RegistryKey.DIALOG, List.copyOf(this.dialogs));
        }
    }

    private record ResponseView(CompoundBinaryTag data) implements DialogResponseView {
        private ResponseView {
            Objects.requireNonNull(data, "data");
        }

        @Override
        public BinaryTagHolder payload() {
            return DialogInternals.encodeCompound(this.data);
        }

        @Override
        public @Nullable String getText(final String key) {
            final BinaryTag value = this.data.get(Objects.requireNonNull(key, "key"));
            return value instanceof StringBinaryTag string ? string.value() : null;
        }

        @Override
        public @Nullable Boolean getBoolean(final String key) {
            final BinaryTag value = this.data.get(Objects.requireNonNull(key, "key"));
            return value instanceof NumberBinaryTag number ? number.byteValue() != 0 : null;
        }

        @Override
        public @Nullable Float getFloat(final String key) {
            final BinaryTag value = this.data.get(Objects.requireNonNull(key, "key"));
            return value instanceof NumberBinaryTag number ? number.floatValue() : null;
        }
    }
}
