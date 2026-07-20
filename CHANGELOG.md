# Changelog

## 1.0.0-SNAPSHOT

- Initial Velocidialog implementation.
- Added direct inline dialogs in initial/repeated CONFIGURATION and PLAY.
- Added a Paper-shaped dialog API with the original signatures, JavaDoc, annotations, defaults,
  and builder flow (`Dialog.create(factory -> factory.empty()...)`).
- Added an inline-only `RegistryKey.DIALOG`/`RegistryValueSet` bridge for dialog lists. Registry
  copying and backend registry synchronization are intentionally unsupported.
- Adapted Paper's item body from Bukkit `ItemStack` to portable Adventure `HoverEvent.ShowItem`.
- Added `BinaryTagHolder` custom additions and response payloads, Paper-style
  `DialogAction.customClick(callback, options)`, the `Dialog.raw(CompoundBinaryTag)` extension,
  protocol NBT codec, managed callbacks, and `Dialogs.showAndAwait`.
- Added Byte Buddy retransformation with a parent-classloader bridge and direct runtime
  instrumentation through `ByteBuddyAgent.install()`; no startup agent or JVM flags are required.
- Added real Fabric Client Game Tests for protocols 771–776 with pinned LOOHP/Limbo backends,
  Velocity compatibility probing, and serialized tag/daily/manual GitHub Actions coverage with
  screenshots and machine-readable result markers.
- Fixed callback registrations remaining live when an awaiting future was completed, failed, or
  cancelled externally.
