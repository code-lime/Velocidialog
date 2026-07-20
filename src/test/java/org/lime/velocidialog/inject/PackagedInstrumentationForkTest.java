package org.lime.velocidialog.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PackagedInstrumentationForkTest {
    private static final String SUCCESS = "PACKAGED_INSTRUMENTATION_OK";

    @Test
    void shadedJarSelfAttachmentWorksInCleanJvm() throws Exception {
        final ForkResult result = runFork(false);
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains(SUCCESS), result.output());
    }

    @Test
    void disabledDynamicLoadingHasActionableDiagnostic() throws Exception {
        final ForkResult result = runFork(true);
        assertNotEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("This JVM must permit dynamic agent loading"),
                result.output());
    }

    private static ForkResult runFork(final boolean disableDynamicLoading) throws Exception {
        final String java = requiredProperty("velocidialog.test.java");
        final String pluginJar = requiredProperty("velocidialog.test.shadowJar");
        final String testClasses = Path.of(
                PackagedInstrumentationForkMain.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString();

        final List<String> command = new ArrayList<>();
        command.add(java);
        if (disableDynamicLoading) {
            command.add("-XX:-EnableDynamicAgentLoading");
        }
        command.add("-cp");
        command.add(testClasses + System.getProperty("path.separator") + pluginJar);
        command.add(PackagedInstrumentationForkMain.class.getName());

        final Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Instrumented child JVM did not exit within 30 seconds");
        }
        final String output = readOutput(process);
        return new ForkResult(process.exitValue(), output);
    }

    private static String readOutput(final Process process) throws IOException {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String requiredProperty(final String key) {
        final String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test system property " + key);
        }
        return value;
    }

    private record ForkResult(int exitCode, String output) {
    }
}
