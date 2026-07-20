package org.lime.velocidialog.protocol;

import io.netty.buffer.ByteBuf;

/** Allocation-free structural validation performed before Adventure recursively parses NBT. */
final class NbtStructureScanner {
    private static final int END = 0;
    private static final int BYTE = 1;
    private static final int SHORT = 2;
    private static final int INT = 3;
    private static final int LONG = 4;
    private static final int FLOAT = 5;
    private static final int DOUBLE = 6;
    private static final int BYTE_ARRAY = 7;
    private static final int STRING = 8;
    private static final int LIST = 9;
    private static final int COMPOUND = 10;
    private static final int INT_ARRAY = 11;
    private static final int LONG_ARRAY = 12;

    private NbtStructureScanner() {
    }

    /** Validates one exact nameless compound, including its root type byte. */
    static void validateCompound(final ByteBuf input, final int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        try {
            final int rootType = input.readUnsignedByte();
            if (rootType != COMPOUND) {
                throw new IllegalArgumentException("Expected compound root, got NBT type " + rootType);
            }
            scanPayload(input, COMPOUND, 0, maxDepth);
            if (input.isReadable()) {
                throw new IllegalArgumentException("Trailing data after compound NBT");
            }
        } catch (final IndexOutOfBoundsException malformed) {
            throw new IllegalArgumentException("Truncated compound NBT", malformed);
        }
    }

    private static void scanPayload(
            final ByteBuf input,
            final int type,
            final int parentDepth,
            final int maxDepth
    ) {
        switch (type) {
            case BYTE -> skip(input, 1);
            case SHORT -> skip(input, 2);
            case INT, FLOAT -> skip(input, 4);
            case LONG, DOUBLE -> skip(input, 8);
            case BYTE_ARRAY -> skipArray(input, 1);
            case STRING -> skipNbtString(input);
            case LIST -> scanList(input, checkedDepth(parentDepth, maxDepth), maxDepth);
            case COMPOUND -> scanCompound(input, checkedDepth(parentDepth, maxDepth), maxDepth);
            case INT_ARRAY -> skipArray(input, Integer.BYTES);
            case LONG_ARRAY -> skipArray(input, Long.BYTES);
            case END -> throw new IllegalArgumentException("Unexpected EndTag payload");
            default -> throw new IllegalArgumentException("Unknown NBT type " + type);
        }
    }

    private static void scanCompound(final ByteBuf input, final int depth, final int maxDepth) {
        while (true) {
            final int childType = input.readUnsignedByte();
            if (childType == END) {
                return;
            }
            requireKnownType(childType);
            skipNbtString(input); // Compound entry name.
            scanPayload(input, childType, depth, maxDepth);
        }
    }

    private static void scanList(final ByteBuf input, final int depth, final int maxDepth) {
        final int elementType = input.readUnsignedByte();
        requireKnownType(elementType);
        final int length = input.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative NBT list length " + length);
        }
        if (elementType == END && length != 0) {
            throw new IllegalArgumentException("A non-empty NBT list cannot contain EndTag elements");
        }

        final int minimumSize = minimumPayloadSize(elementType);
        if (minimumSize != 0 && (long) length * minimumSize > input.readableBytes()) {
            throw new IllegalArgumentException("NBT list length exceeds its encoded payload");
        }
        for (int index = 0; index < length; index++) {
            scanPayload(input, elementType, depth, maxDepth);
        }
    }

    private static void skipArray(final ByteBuf input, final int elementSize) {
        final int length = input.readInt();
        if (length < 0) {
            throw new IllegalArgumentException("Negative NBT array length " + length);
        }
        final long bytes = (long) length * elementSize;
        if (bytes > input.readableBytes()) {
            throw new IllegalArgumentException("NBT array length exceeds its encoded payload");
        }
        input.skipBytes((int) bytes);
    }

    private static void skipNbtString(final ByteBuf input) {
        skip(input, input.readUnsignedShort());
    }

    private static void skip(final ByteBuf input, final int bytes) {
        if (bytes > input.readableBytes()) {
            throw new IllegalArgumentException("NBT payload is truncated");
        }
        input.skipBytes(bytes);
    }

    private static int checkedDepth(final int parentDepth, final int maxDepth) {
        final int depth = parentDepth + 1;
        if (depth > maxDepth) {
            throw new IllegalArgumentException("NBT is deeper than " + maxDepth);
        }
        return depth;
    }

    private static void requireKnownType(final int type) {
        if (type < END || type > LONG_ARRAY) {
            throw new IllegalArgumentException("Unknown NBT type " + type);
        }
    }

    private static int minimumPayloadSize(final int type) {
        return switch (type) {
            case END -> 0;
            case BYTE -> 1;
            case SHORT, STRING -> 2;
            case INT, FLOAT, BYTE_ARRAY, INT_ARRAY, LONG_ARRAY -> 4;
            case LONG, DOUBLE -> 8;
            case LIST -> 5;
            case COMPOUND -> 1;
            default -> throw new IllegalArgumentException("Unknown NBT type " + type);
        };
    }
}
