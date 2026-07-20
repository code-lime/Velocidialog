# Velocidialog

Velocidialog implements Kyori dialogs on Velocity, including the initial and repeated
`CONFIGURATION` phase before a backend server is available. It replaces Velocity's unsupported
`Player.showDialog(DialogLike)` and `Player.closeDialog()` defaults with a native packet transport
and consumes only callbacks owned by this plugin. Foreign custom-click packets continue to the
backend unchanged.

Supported client protocols:

| Minecraft | Protocol |
|---|---:|
| 1.21.6 | 771 |
| 1.21.7–1.21.8 | 772 |
| 1.21.9–1.21.10 | 773 |
| 1.21.11 | 774 |
| 26.1–26.1.2 | 775 |
| 26.2 | 776 |

Unknown future packet mappings are never guessed. Calls through `Player.showDialog` are a no-op
for older/unknown clients; `Dialogs.showAndAwait` completes exceptionally so an awaiting
configuration event cannot hang silently.

## Installation

Run Gradle with Java 21 and make a JDK 25 toolchain available. JDK 25 is used only to compile and
run the Velocity 4 compatibility suite; the plugin jar itself targets Java 21:

```shell
./gradlew clean build
```

Copy the single `build/libs/Velocidialog-<version>.jar` to `plugins/`. The same plugin jar targets
Velocity 3.4 with Adventure 4.26 and Velocity 4 with Adventure 5.2. For the 3.4 snapshot line, the
minimum compatible build is `3.4.0-SNAPSHOT#532`; `#527` predates Velocity's
`ServerboundCustomClickActionPacket` and is not applicable.

Velocidialog obtains JVM instrumentation directly through `ByteBuddyAgent.install()`. It does not
need a separate startup-agent jar, `-javaagent`, `-Djdk.attach.allowAttachSelf`, or
`-XX:+EnableDynamicAgentLoading`. Startup deliberately fails with an actionable diagnostic if the
JVM or container forbids dynamic attachment.

Recent JDKs may print the dynamic-agent warning introduced by
[JEP 451](https://openjdk.org/jeps/451). The JEP also describes the intended future change to
disable dynamic agent loading by default; a runtime that enforces that restriction cannot run this
plugin until the operator permits attachment for that JVM.

Startup also fails if retransformation or the checked Velocity 3.4/4.0 internal entry points are
unavailable.

## Real-client tests

`tests/fabric-client` is one isolated Gradle 9.5.1 project for every supported protocol. It uses
Fabric Loader 0.19.3, Loom 1.17.16, and official Mojang names only: 1.21.x selects
`loom.officialMojangMappings()`, while unobfuscated 26.x needs no mappings dependency. Yarn is not
used. The exact CI variants are `1.21.6`, `1.21.8`, `1.21.10`, `1.21.11`, `26.1.2`, and `26.2`.

Select a version with `-Pvariable`; `1.21.6` is the default:

```shell
tests/fabric-client/gradlew -p tests/fabric-client compileGametestJava -Pvariable=1.21.6
tests/fabric-client/gradlew -p tests/fabric-client compileGametestJava -Pvariable=26.2
```

The reusable GitHub Actions test first probes each downloaded Velocity jar. Inapplicable builds are
reported without starting a client; applicable builds run their intersection with protocols
771–776 sequentially under Xvfb. Each test uses Fabric's `TestDedicatedServerContext` to start an
in-process Minecraft dedicated server on `127.0.0.1:25566` in offline mode, connects a real client
through Velocity, exercises CONFIGURATION and PLAY callbacks, verifies `closeDialog()`, and keeps
logs, crash reports, screenshots, and result markers. GitHub Actions launches Client E2E for tags
and scheduled/manual Velocity-build checks. Push and pull requests run only the ordinary Gradle
verification suite and never launch Minecraft; `runClientGameTest` remains available for a
manually prepared local E2E environment.

## CONFIGURATION form

`PlayerConfigurationEvent` is an awaiting event. The listener must return
`EventTask.resumeWhenComplete(...)`; this is what keeps the connection in `CONFIGURATION` until a
response, disconnect, or timeout. Merely calling `Player.showDialog()` does not alter Velocity's
lifecycle.

```java
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.lime.velocidialog.api.dialog.ActionButton;
import org.lime.velocidialog.api.dialog.Dialog;
import org.lime.velocidialog.api.dialog.DialogBase;
import org.lime.velocidialog.api.dialog.Dialogs;
import org.lime.velocidialog.api.dialog.input.DialogInput;
import org.lime.velocidialog.api.dialog.type.DialogType;

@Subscribe
public EventTask configurePlayer(PlayerConfigurationEvent event) {
    var response = Dialogs.showAndAwait(
        event.player(),
        submit -> Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Component.text("Proxy setup"))
                .body(List.of())
                .inputs(List.of(
                    DialogInput.text("nickname", Component.text("Nickname"))
                        .maxLength(16)
                        .build()
                ))
                .build())
            .type(DialogType.notice(
                ActionButton.builder(Component.text("Continue"))
                    .action(submit)
                    .build()
            ))),
        Duration.ofSeconds(30)
    ).toCompletableFuture();

    return EventTask.resumeWhenComplete(response.thenAccept(values -> {
        String nickname = values.getText("nickname"); // client input is untrusted
        // Validate and store it before CONFIGURATION is allowed to finish.
    }));
}
```

The overload without `Duration` uses 30 seconds. Timeout and disconnect complete the returned
stage exceptionally. Timeout does not close the current client dialog because another plugin may
already have replaced it.

Calls from `PostLoginEvent`, `PlayerEnteredConfigurationEvent`, and
`PlayerConfigurationEvent` are dispatched on the player's event loop. During the initial login,
`PostLoginEvent` already runs with the clientbound protocol in `CONFIGURATION`, so the dialog can
be sent before Velocity connects a backend. Initial configuration and backend-triggered
reconfiguration use the same direct, inline dialog encoding and do not require a
`ServerConnection`.

## API model

The package `org.lime.velocidialog.api.dialog` follows Paper's dialog API, including its factory,
builder, annotation, validation, and JavaDoc contracts. A normal direct dialog starts with
`factory.empty()`:

```java
Dialog notice = Dialog.create(factory -> factory.empty()
    .base(DialogBase.builder(Component.text("Information")).build())
    .type(DialogType.notice()));
```

The supported model includes:

- five layouts: confirmation, notice, multi-action, dialog-list, and server-links;
- plain-message and item bodies;
- boolean, number-range, single-option, and text inputs;
- static click, command-template, custom-click, and managed callback actions;
- `DialogResponseView.payload()` as an immutable `BinaryTagHolder`, plus nullable
  `getText`/`getBoolean`/`getFloat` convenience accessors;
- Paper defaults and validation for dimensions, input keys, initial values, text limits, and
  `pause`/`after_action` combinations.

There are three intentional Velocity-specific adaptations:

- `ItemDialogBody` accepts Adventure's `HoverEvent.ShowItem` instead of Bukkit's `ItemStack`. This
  keeps item keys, counts, and modern data components portable across Velocity and both supported
  Adventure generations; legacy item NBT is rejected because it cannot be losslessly converted.
- The registry bridge is direct-only. `RegistryKey.DIALOG` exists so Paper-shaped APIs retain their
  types, but dialogs are anonymous inline values and are never synchronized with a backend registry.
- `Dialog.raw(CompoundBinaryTag)` is a Velocidialog extension for a compound already accepted by
  Minecraft's direct dialog codec.

Create an inline dialog list either from existing direct dialogs:

```java
var dialogs = RegistrySet.valueSet(RegistryKey.DIALOG, List.of(firstDialog, secondDialog));

Dialog list = Dialog.create(factory -> factory.empty()
    .base(DialogBase.builder(Component.text("Choose")).build())
    .type(DialogType.dialogList(dialogs).build()));
```

or with the entry builder's Paper-style direct-value-set builder:

```java
Dialog list = Dialog.create(factory -> {
    DialogRegistryEntry.Builder entry = factory.empty();
    var dialogs = entry.registryValueSet()
        .add(nested -> nested.empty()
            .base(DialogBase.builder(Component.text("Nested")).build())
            .type(DialogType.notice()))
        .build();

    entry.base(DialogBase.builder(Component.text("Choose")).build())
        .type(DialogType.dialogList(dialogs).build());
});
```

Custom additions and raw responses use `BinaryTagHolder`, matching Paper:

```java
BinaryTagHolder additions = BinaryTagHolder.binaryTagHolder("{source:\"proxy\"}");
DialogAction.CustomClickAction submit = DialogAction.customClick(
    Key.key("example:submit"),
    additions
);

BinaryTagHolder untrustedPayload = response.payload();
```

Managed callbacks use only Paper's `customClick(callback, options)` entry point. The returned value
is a normal `CustomClickAction`; callback metadata is not exposed as another public action subtype:

```java
DialogAction.CustomClickAction callback = DialogAction.customClick(
    (response, audience) -> {
        String value = response.getText("value"); // validate all client data
    },
    ClickCallback.Options.builder()
        .uses(1)
        .lifetime(Duration.ofMinutes(5))
        .build()
);
```

All dialogs produced by `Dialog.create(...)`, including dialog-list children, are direct/inline.
Other arbitrary `DialogLike` implementations are rejected because Kyori's `DialogLike` is only a
marker and contains no serializable data.

Calling the familiar Audience methods is supported after Velocidialog starts:

```java
player.showDialog(dialog);
player.closeDialog();
```

Forwarding audiences work because Adventure ultimately invokes these transformed `Player`
methods.

## Dependency

Published API coordinates are:

```groovy
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/code-lime/velocidialog')
        credentials {
            username = providers.gradleProperty('gpr.user').orNull
            password = providers.gradleProperty('gpr.key').orNull
        }
    }
}

dependencies {
    compileOnly 'org.lime:velocidialog:1.0.0-SNAPSHOT'
}
```

For GitHub Packages, set `gpr.user` and a `gpr.key` with `read:packages` in your user-level
`gradle.properties`.

Also declare the runtime plugin dependency so Velocity loads Velocidialog before the consuming
plugin. For a JSON plugin descriptor:

```json
{
  "dependencies": [
    { "id": "velocidialog", "optional": false }
  ]
}
```

Adventure API is owned by Velocity. `adventure-nbt` is embedded without relocation because not all
Velocity distributions ship that optional module; parent-first loading still selects Velocity's
copy when one is present. Byte Buddy is relocated, while its small attachment entry point retains
the canonical package required by the JVM attach protocol.
