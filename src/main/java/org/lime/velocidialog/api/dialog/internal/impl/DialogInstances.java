package org.lime.velocidialog.api.dialog.internal.impl;

import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.DialogInstancesProvider;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.action.DialogActionCallback;
import org.lime.velocidialog.api.dialog.body.ItemDialogBody;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.input.BooleanDialogInput;
import org.lime.velocidialog.api.dialog.input.NumberRangeDialogInput;
import org.lime.velocidialog.api.dialog.input.SingleOptionDialogInput;
import org.lime.velocidialog.api.dialog.input.TextDialogInput;
import org.lime.velocidialog.api.dialog.internal.DialogInternals;
import org.lime.velocidialog.api.dialog.type.ConfirmationType;
import org.lime.velocidialog.api.dialog.type.DialogListType;
import org.lime.velocidialog.api.dialog.type.MultiActionType;
import org.lime.velocidialog.api.dialog.type.NoticeType;
import org.lime.velocidialog.api.dialog.type.ServerLinksType;
import org.lime.velocidialog.api.registry.set.RegistrySet;

/** Local implementation provider used in place of Paper's server-side service provider. */
public final class DialogInstances implements DialogInstancesProvider {
    public static final DialogInstancesProvider INSTANCE = new DialogInstances();

    private DialogInstances() {
    }

    @Override
    public DialogBase.Builder dialogBaseBuilder(final Component title) {
        return new DialogBaseImpl.BuilderImpl(title);
    }

    @Override
    public ActionButton.Builder actionButtonBuilder(final Component label) {
        return new ActionButtonImpl.BuilderImpl(label);
    }

    @Override
    public DialogAction.CustomClickAction register(
            final DialogActionCallback callback,
            final ClickCallback.Options options) {
        return DialogInternals.registerCallback(callback, options);
    }

    @Override
    public DialogAction.StaticAction staticAction(final ClickEvent value) {
        return new StaticActionImpl(value);
    }

    @Override
    public DialogAction.CommandTemplateAction commandTemplate(final String template) {
        return new CommandTemplateActionImpl(template);
    }

    @Override
    public DialogAction.CustomClickAction customClick(
            final Key id,
            final @Nullable BinaryTagHolder additions) {
        return new CustomClickActionImpl(id, additions);
    }

    @Override
    public ItemDialogBody.Builder itemDialogBodyBuilder(final HoverEvent.ShowItem item) {
        return new ItemDialogBodyImpl.BuilderImpl(item);
    }

    @Override
    public PlainMessageDialogBody plainMessageDialogBody(final Component component) {
        return new PlainMessageBodyImpl(component);
    }

    @Override
    public PlainMessageDialogBody plainMessageDialogBody(
            final Component component,
            final int width) {
        return new PlainMessageBodyImpl(component, width);
    }

    @Override
    public BooleanDialogInput.Builder booleanBuilder(
            final String key,
            final Component label) {
        return new BooleanDialogInputImpl.BuilderImpl(key, label);
    }

    @Override
    public NumberRangeDialogInput.Builder numberRangeBuilder(
            final String key,
            final Component label,
            final float start,
            final float end) {
        return new NumberRangeDialogInputImpl.BuilderImpl(key, label, start, end);
    }

    @Override
    public SingleOptionDialogInput.Builder singleOptionBuilder(
            final String key,
            final Component label,
            final List<SingleOptionDialogInput.OptionEntry> entries) {
        return new SingleOptionDialogInputImpl.BuilderImpl(key, entries, label);
    }

    @Override
    public SingleOptionDialogInput.OptionEntry singleOptionEntry(
            final String id,
            final @Nullable Component display,
            final boolean initial) {
        return new SingleOptionDialogInputImpl.SingleOptionEntryImpl(id, display, initial);
    }

    @Override
    public TextDialogInput.Builder textBuilder(final String key, final Component label) {
        return new TextDialogInputImpl.BuilderImpl(key, label);
    }

    @Override
    public TextDialogInput.MultilineOptions multilineOptions(
            final @Nullable Integer maxLines,
            final @Nullable Integer height) {
        return new TextDialogInputImpl.MultilineOptionsImpl(maxLines, height);
    }

    @Override
    public ConfirmationType confirmation(
            final ActionButton yesButton,
            final ActionButton noButton) {
        return new ConfirmationTypeImpl(yesButton, noButton);
    }

    @Override
    public DialogListType.Builder dialogList(final RegistrySet<Dialog> dialogs) {
        return new DialogListTypeImpl.BuilderImpl(dialogs);
    }

    @Override
    public MultiActionType.Builder multiAction(final List<ActionButton> actions) {
        return new MultiActionTypeImpl.BuilderImpl(actions);
    }

    @Override
    public NoticeType notice() {
        return new NoticeTypeImpl();
    }

    @Override
    public NoticeType notice(final ActionButton action) {
        return new NoticeTypeImpl(action);
    }

    @Override
    public ServerLinksType serverLinks(
            final @Nullable ActionButton exitAction,
            final int columns,
            final int buttonWidth) {
        return new ServerLinksTypeImpl(exitAction, columns, buttonWidth);
    }
}
