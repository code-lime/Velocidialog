package org.lime.velocidialog.inject;

import com.velocitypowered.api.proxy.VelocidialogBridge;
import com.velocitypowered.api.proxy.Player;
import net.bytebuddy.asm.Advice;

final class PlayerCloseAdvice {
    private PlayerCloseAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(@Advice.This final Object player) {
        if (!(player instanceof Player)) {
            return false;
        }
        VelocidialogBridge.close(player);
        return true;
    }
}
