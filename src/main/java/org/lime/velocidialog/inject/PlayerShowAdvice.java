package org.lime.velocidialog.inject;

import com.velocitypowered.api.proxy.VelocidialogBridge;
import com.velocitypowered.api.proxy.Player;
import net.bytebuddy.asm.Advice;

final class PlayerShowAdvice {
    private PlayerShowAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(@Advice.This final Object player, @Advice.Argument(0) final Object dialog) {
        if (!(player instanceof Player)) {
            return false;
        }
        VelocidialogBridge.show(player, dialog);
        return true;
    }
}
