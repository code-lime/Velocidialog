# Mojang `Dialog.DIRECT_CODEC` fixtures (Minecraft 1.21.6)

These are frozen compatibility vectors, not hand-written expectations. Each `.json` file was
accepted by the official Minecraft 1.21.6 `Dialog.DIRECT_CODEC`; the matching `.snbt` file is the
result of encoding that decoded dialog through the same codec with `NbtOps.INSTANCE`.

The runtime is deliberately not a Gradle dependency. A Minecraft server bundle is about 58 MB,
is obfuscated, and is not an ordinary Maven test artifact. Keeping it out of the dependency graph
makes normal CI portable and ensures that no Mojang/Paper implementation classes enter the plugin
jar. The tests parse these SNBT results with Adventure NBT and compare structural tag equality.

## Provenance

- Minecraft release: `1.21.6` (2025-06-17), Java 21.
- Mojang server URL:
  `https://piston-data.mojang.com/v1/objects/6e64dcabba3c01a7271b4fa6bd898483b794c59b/server.jar`
- Server bundle SHA-1 from Mojang metadata: `6e64dcabba3c01a7271b4fa6bd898483b794c59b`.
- Server bundle SHA-256:
  `08abf384c48afb9e822144ad8a99166482857994389269e26c6a04c6c91d9171`.
- Nested `server-1.21.6.jar` SHA-256:
  `4ebf329faca140f5383efdb3002b2b0311ed1e0df1415b43fa23fb98cfab1a56`.
- Mojang server mappings URL:
  `https://piston-data.mojang.com/v1/objects/94d453080a58875d3acc1a9a249809767c91ed40/server.txt`
- Mappings SHA-1 from Mojang metadata: `94d453080a58875d3acc1a9a249809767c91ed40`.
- Mappings SHA-256:
  `c6fe95810b05dfec19fdbdfb8cbbb8f976923f7cb16fcc828a0b605a49d2c492`.

The mappings identify `Dialog.DIRECT_CODEC` as `art.c`, `NbtOps.INSTANCE` as `uw.a`,
`SharedConstants.tryDetectVersion()` as `ac.a()`, and `Bootstrap.bootStrap()` as `amg.a()`.
The probe performs exactly:

1. bootstrap the official server registries;
2. `Dialog.DIRECT_CODEC.parse(JsonOps.INSTANCE, fixtureJson)`;
3. `Dialog.DIRECT_CODEC.encodeStart(NbtOps.INSTANCE, decodedDialog)`;
4. persist the resulting tag as SNBT.

Paper uses this same direct Mojang codec for inline registry-file dialog decoding. This was checked
against official Paper commit `dff5410a5936dd9125963f8b00c430bd9c4566ec`, in
`PaperDialogCodecs.DIALOG_CODEC`, which delegates to `registryFileDecoderFor(Dialog.DIRECT_CODEC, ...)`.

## Regeneration

With the two official files downloaded (or the copies under `tmp/`) and a Java 21 JDK:

```powershell
./tools/dialog-fixtures/Generate-OfficialDialogFixtures.ps1 `
  -ServerBundle ./tmp/mojang-server-1.21.6.jar `
  -ServerMappings ./tmp/mojang-server-1.21.6-mappings.txt `
  -JavaHome C:/path/to/jdk-21
```

The script verifies both SHA-256 values before executing the codec. When updating the Minecraft
baseline, review the official mappings, update the probe symbols and hashes, regenerate every
fixture, and inspect the diff. These vectors prove the covered codec shapes; they are not a claim
that an old 1.21.6 codec validates fields introduced by a later protocol.
