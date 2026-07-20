package org.lime.velocidialog.inject;

import com.velocitypowered.api.proxy.VelocidialogBridge;
import net.bytebuddy.asm.Advice;

final class ConfigurationClickAdvice {
    private ConfigurationClickAdvice() {
    }

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    static boolean enter(
            @Advice.This final Object packet,
            @Advice.Argument(0) final Object sessionHandler) {
        return VelocidialogBridge.configurationClick(sessionHandler, packet);
    }

    @Advice.OnMethodExit
    static void exit(
            @Advice.Enter final boolean consumed,
            @Advice.Return(readOnly = false) boolean handled) {
        if (consumed) {
            handled = true;
        }
    }
}
