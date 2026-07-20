package probe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Runs the obfuscated Mojang 1.21.6 Dialog.DIRECT_CODEC without redistributing server classes. */
public final class OfficialDialogCodecProbe {
    private OfficialDialogCodecProbe() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length == 0 || (args.length & 1) != 0) {
            throw new IllegalArgumentException("Expected name/base64-json argument pairs");
        }

        // Official server mappings for 1.21.6:
        // SharedConstants.tryDetectVersion -> ac.a; Bootstrap.bootStrap -> amg.a.
        final Class<?> sharedConstantsClass = Class.forName("ac");
        final Method detectVersion = sharedConstantsClass.getDeclaredMethod("a");
        detectVersion.setAccessible(true);
        detectVersion.invoke(null);

        final Class<?> bootstrapClass = Class.forName("amg");
        final Method bootstrap = bootstrapClass.getDeclaredMethod("a");
        bootstrap.setAccessible(true);
        bootstrap.invoke(null);

        // Dialog.DIRECT_CODEC -> art.c; NbtOps.INSTANCE -> uw.a.
        final Class<?> dialogClass = Class.forName("art");
        final Field directCodecField = dialogClass.getDeclaredField("c");
        directCodecField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Codec<Object> directCodec = (Codec<Object>) directCodecField.get(null);

        final Class<?> nbtOpsClass = Class.forName("uw");
        final Field nbtOpsInstanceField = nbtOpsClass.getDeclaredField("a");
        nbtOpsInstanceField.setAccessible(true);
        @SuppressWarnings("unchecked")
        final DynamicOps<Object> nbtOps = (DynamicOps<Object>) nbtOpsInstanceField.get(null);

        for (int index = 0; index < args.length; index += 2) {
            final String name = args[index];
            final String json = new String(
                    Base64.getDecoder().decode(args[index + 1]),
                    StandardCharsets.UTF_8
            );
            final JsonElement input = JsonParser.parseString(json);
            final Object dialog = directCodec.parse(JsonOps.INSTANCE, input).getOrThrow();
            final Object encoded = directCodec.encodeStart(nbtOps, dialog).getOrThrow();
            System.out.println("DIRECT_CODEC_FIXTURE " + name + " " + encoded);
        }
    }
}
