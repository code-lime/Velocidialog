package org.lime.velocidialog.api.dialog;

import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.api.dialog.action.DialogActionCallback;
import org.lime.velocidialog.api.dialog.body.ItemDialogBody;
import org.lime.velocidialog.api.dialog.body.PlainMessageDialogBody;
import org.lime.velocidialog.api.dialog.input.BooleanDialogInput;
import org.lime.velocidialog.api.dialog.input.NumberRangeDialogInput;
import org.lime.velocidialog.api.dialog.input.SingleOptionDialogInput;
import org.lime.velocidialog.api.dialog.input.TextDialogInput;
import org.lime.velocidialog.api.dialog.type.ConfirmationType;
import org.lime.velocidialog.api.dialog.type.DialogListType;
import org.lime.velocidialog.api.dialog.type.MultiActionType;
import org.lime.velocidialog.api.dialog.type.NoticeType;
import org.lime.velocidialog.api.dialog.type.ServerLinksType;
import org.lime.velocidialog.api.registry.set.RegistrySet;

/**
 * @hidden
 */
@SuppressWarnings("MissingJavadoc")
@ApiStatus.Internal
public interface DialogInstancesProvider {

    static DialogInstancesProvider instance() {
        final class Holder {
            static final DialogInstancesProvider INSTANCE = org.lime.velocidialog.api.dialog.internal.impl.DialogInstances.INSTANCE;
        }
        return Holder.INSTANCE;
    }

    DialogBase.Builder dialogBaseBuilder(Component title);

    ActionButton.Builder actionButtonBuilder(Component label);

    // actions
    DialogAction.CustomClickAction register(DialogActionCallback callback, ClickCallback.Options options);

    DialogAction.StaticAction staticAction(ClickEvent value);

    DialogAction.CommandTemplateAction commandTemplate(String template);

    DialogAction.CustomClickAction customClick(Key id, @Nullable BinaryTagHolder additions);

    // bodies
    ItemDialogBody.Builder itemDialogBodyBuilder(HoverEvent.ShowItem item);

    PlainMessageDialogBody plainMessageDialogBody(Component component);

    PlainMessageDialogBody plainMessageDialogBody(Component component, int width);

    // inputs
    BooleanDialogInput.Builder booleanBuilder(String key, Component label);

    NumberRangeDialogInput.Builder numberRangeBuilder(String key, Component label, float start, float end);

    SingleOptionDialogInput.Builder singleOptionBuilder(String key, Component label, List<SingleOptionDialogInput.OptionEntry> entries);

    SingleOptionDialogInput.OptionEntry singleOptionEntry(String id, @Nullable Component display, boolean initial);

    TextDialogInput.Builder textBuilder(String key, Component label);

    TextDialogInput.MultilineOptions multilineOptions(@Nullable Integer maxLines, @Nullable Integer height);

    // types
    ConfirmationType confirmation(ActionButton yesButton, ActionButton noButton);

    DialogListType.Builder dialogList(RegistrySet<Dialog> dialogs);

    MultiActionType.Builder multiAction(List<ActionButton> actions);

    NoticeType notice();

    NoticeType notice(ActionButton action);

    ServerLinksType serverLinks(@Nullable ActionButton exitAction, int columns, int buttonWidth);
}
