package org.lime.velocidialog.runtime;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.lime.velocidialog.api.dialog.action.DialogAction;
import org.lime.velocidialog.protocol.CustomClickPacketBody;
import org.lime.velocidialog.protocol.DialogPacketBodies;

final class DialogPacketBodiesTestSupport {
    private DialogPacketBodiesTestSupport() {
    }

    static ByteBuf customClickBody(
            final DialogAction.CustomClickAction action,
            final CompoundBinaryTag payload) {
        final ByteBuf body = Unpooled.buffer();
        DialogPacketBodies.writeCustomClick(body, new CustomClickPacketBody(
                action.id().asString(), payload));
        return body;
    }
}
