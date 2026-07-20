# Runtime dependencies

- Velocity API 3.4 or 4.0 (provided by the proxy; minimum 3.4 snapshot build `#532`)
- Kyori Adventure API (provided by Velocity)
- Kyori Adventure NBT 4.26.1 (embedded without relocation as a clean-Velocity fallback)
- Netty (provided by Velocity)
- Byte Buddy 1.17.5 and Byte Buddy Agent 1.17.5 (embedded in the single plugin jar; attached at
  runtime through `ByteBuddyAgent.install()`)

# Public API dependencies

The dialog API follows Paper's signatures and annotations but does not depend on Paper or Bukkit.
Velocity supplies Adventure `Component`, `Audience`, `DialogLike`, `ClickEvent`, `ClickCallback`,
`Key`, and `BinaryTagHolder` types. `adventure-nbt` is also embedded because
`Dialog.raw(CompoundBinaryTag)` is a Velocidialog extension and a clean Velocity installation may
not include that optional module.

The published API metadata also exposes its documentation annotations:

- JetBrains annotations for `@ApiStatus`, `@Contract`, `@Range`, and `@Unmodifiable`;
- JSpecify for `@NullMarked` and `@Nullable`;
- Checker Framework qualifier annotations for `@Positive`.

The registry compatibility layer is intentionally direct-only. It provides `RegistryKey.DIALOG`,
`RegistryBuilderFactory`, and direct `RegistryValueSet` values for Paper-shaped dialog creation;
it does not connect to or copy entries from a backend registry. `copyFrom(...)` is unsupported.

The one model-type substitution is `HoverEvent.ShowItem` in place of Bukkit `ItemStack`. This is
required to preserve portable item and data-component serialization on a proxy without Bukkit.

The API surface and JavaDoc were ported from the official Paper repository at commit
`dff5410a5936dd9125963f8b00c430bd9c4566ec`, then adapted for the direct-only Velocity model.
Paper and this project are distributed under GPLv3; see `LICENSE`.

# Compatibility matrix

- Velocity 3.4, Adventure 4.26.1, Java 21
- Velocity 4.0, Adventure 5.2, Java 25 compatibility tests; the plugin artifact still targets Java 21
