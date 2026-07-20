package org.lime.velocidialog.inject;

import com.velocitypowered.api.proxy.VelocidialogBridge;
import net.bytebuddy.asm.Advice;

final class PlayClickAdvice {
    private PlayClickAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(
            @Advice.FieldValue("player") final Object player,
            @Advice.Argument(0) final Object buffer) {
        return VelocidialogBridge.playClick(player, buffer);
    }
}

