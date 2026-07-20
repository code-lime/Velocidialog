package org.lime.velocidialog.e2e;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Emits a shell-friendly list of Velocidialog client variants supported by a Velocity jar. */
public final class VelocityCompatibilityProbe {
    private static final String REQUIRED_PACKET =
            "com/velocitypowered/proxy/protocol/packet/ServerboundCustomClickActionPacket.class";
    private static final Map<Integer, String> VARIANTS = Map.of(
            771, "1.21.6",
            772, "1.21.8",
            773, "1.21.10",
            774, "1.21.11",
            775, "26.1.2",
            776, "26.2");

    private VelocityCompatibilityProbe() {
    }

    public static void main(final String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Usage: VelocityCompatibilityProbe <velocity.jar>");
        }
        final Path velocityJar = Path.of(arguments[0]).toAbsolutePath().normalize();
        try (JarFile jar = new JarFile(velocityJar.toFile())) {
            if (jar.getJarEntry(REQUIRED_PACKET) == null) {
                System.out.println("VELOCIDIALOG_APPLICABLE=false");
                System.out.println("VELOCIDIALOG_VARIANTS=");
                return;
            }
        }

        final List<String> supported = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{velocityJar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            final Class<?> protocolVersion = Class.forName(
                    "com.velocitypowered.api.network.ProtocolVersion", true, loader);
            final Method isSupported = protocolVersion.getMethod("isSupported", int.class);
            for (Map.Entry<Integer, String> variant : VARIANTS.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                if (Boolean.TRUE.equals(isSupported.invoke(null, variant.getKey()))) {
                    supported.add(variant.getValue());
                }
            }
        }

        System.out.println("VELOCIDIALOG_APPLICABLE=true");
        System.out.println("VELOCIDIALOG_VARIANTS=" + String.join(",", supported));
    }
}
