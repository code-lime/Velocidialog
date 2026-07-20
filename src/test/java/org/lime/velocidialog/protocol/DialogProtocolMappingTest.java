package org.lime.velocidialog.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DialogProtocolMappingTest {
    @Test
    void mapsEveryVelocitySupportedDialogProtocolExactly() {
        assertMapping(771, 0x41, 0x84, 0x85);
        assertMapping(772, 0x41, 0x84, 0x85);
        assertMapping(773, 0x41, 0x89, 0x8A);
        assertMapping(774, 0x41, 0x89, 0x8A);
        assertMapping(775, 0x44, 0x8B, 0x8C);
        assertMapping(776, 0x44, 0x8B, 0x8C);
    }

    @Test
    void rejectsProtocolsWithoutNativeDialogs() {
        assertFalse(DialogProtocolMapping.supports(770));
        assertFalse(DialogProtocolMapping.supports(777));
        assertThrows(IllegalArgumentException.class, () -> DialogProtocolMapping.forProtocol(770));
    }

    private static void assertMapping(
            final int protocol,
            final int customClick,
            final int clearDialog,
            final int showDialog
    ) {
        assertTrue(DialogProtocolMapping.supports(protocol));
        final DialogProtocolMapping mapping = DialogProtocolMapping.forProtocol(protocol);
        assertEquals(protocol, mapping.protocolVersion());
        assertEquals(0x08, mapping.customClick(ProtocolPhase.CONFIGURATION));
        assertEquals(0x11, mapping.clearDialog(ProtocolPhase.CONFIGURATION));
        assertEquals(0x12, mapping.showDialog(ProtocolPhase.CONFIGURATION));
        assertEquals(customClick, mapping.customClick(ProtocolPhase.PLAY));
        assertEquals(clearDialog, mapping.clearDialog(ProtocolPhase.PLAY));
        assertEquals(showDialog, mapping.showDialog(ProtocolPhase.PLAY));
    }
}
