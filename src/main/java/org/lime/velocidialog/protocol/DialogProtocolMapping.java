package org.lime.velocidialog.protocol;

import java.util.Map;

/**
 * Packet identifiers used by the native dialog protocol.
 *
 * <p>The configuration identifiers have remained stable since dialogs were
 * introduced in protocol 771. Play identifiers are deliberately listed per
 * protocol instead of inferred from neighbouring packets.</p>
 */
public record DialogProtocolMapping(
        int protocolVersion,
        int configurationCustomClick,
        int configurationClearDialog,
        int configurationShowDialog,
        int playCustomClick,
        int playClearDialog,
        int playShowDialog
) {
    public static final int MINIMUM_PROTOCOL = 771;
    public static final int MAXIMUM_PROTOCOL = 776;

    private static final Map<Integer, DialogProtocolMapping> MAPPINGS = Map.of(
            771, mapping(771, 0x41, 0x84, 0x85),
            772, mapping(772, 0x41, 0x84, 0x85),
            773, mapping(773, 0x41, 0x89, 0x8A),
            774, mapping(774, 0x41, 0x89, 0x8A),
            775, mapping(775, 0x44, 0x8B, 0x8C),
            776, mapping(776, 0x44, 0x8B, 0x8C)
    );

    private static DialogProtocolMapping mapping(
            final int protocol,
            final int playCustomClick,
            final int playClearDialog,
            final int playShowDialog
    ) {
        return new DialogProtocolMapping(
                protocol,
                0x08,
                0x11,
                0x12,
                playCustomClick,
                playClearDialog,
                playShowDialog
        );
    }

    /** Returns whether the protocol has native dialogs. */
    public static boolean supports(final int protocolVersion) {
        return MAPPINGS.containsKey(protocolVersion);
    }

    /**
     * Looks up the exact packet mapping for a protocol.
     *
     * @throws IllegalArgumentException if the version is outside the supported matrix
     */
    public static DialogProtocolMapping forProtocol(final int protocolVersion) {
        final DialogProtocolMapping mapping = MAPPINGS.get(protocolVersion);
        if (mapping == null) {
            throw new IllegalArgumentException(
                    "Native dialogs are not mapped for protocol " + protocolVersion
                            + " (supported: " + MINIMUM_PROTOCOL + ".." + MAXIMUM_PROTOCOL + ')'
            );
        }
        return mapping;
    }

    public int customClick(final ProtocolPhase phase) {
        return phase == ProtocolPhase.CONFIGURATION ? this.configurationCustomClick : this.playCustomClick;
    }

    public int clearDialog(final ProtocolPhase phase) {
        return phase == ProtocolPhase.CONFIGURATION ? this.configurationClearDialog : this.playClearDialog;
    }

    public int showDialog(final ProtocolPhase phase) {
        return phase == ProtocolPhase.CONFIGURATION ? this.configurationShowDialog : this.playShowDialog;
    }
}
