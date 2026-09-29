# 1.21.1 → 1.20.1 (Forge) backport tools

Codemods used to backport OpenComputers: Modernized from NeoForge/Fabric 1.21.1 to Forge 1.20.1.
They are kept to re-apply the mechanical part of the port when merging upstream changes.

Run from the repository root, in this order, then fix the remaining compile errors by hand.

| Step | Command |
|------|---------|
| `ResourceLocation` factories | `python3 tools/backport/resource_location.py api core` |
| Custom data component | `grep -rl 'net.minecraft.core.component.DataComponents.CUSTOM_DATA' --include=*.java api core \| xargs sed -i 's/net\.minecraft\.core\.component\.DataComponents\.CUSTOM_DATA/DataComponents.CUSTOM_DATA/g'`<br>`python3 tools/backport/custom_data.py api core` |
| Item stack NBT | `python3 tools/backport/item_stack_nbt.py api core` |
| `Math.clamp` (Java 21) | replace `Math.clamp(` with `MathCompat.clamp(` and import `li.cil.oc.compat.MathCompat` |
| Pattern `switch` (Java 21) | `python3 tools/backport/pattern_switch.py <files javac reports>` |
| Vertex builder chains | `python3 tools/backport/vertex_chain.py core` |
| `List.getFirst()` & co. (Java 21) | `python3 tools/backport/sequenced_collection.py <javac error log>` |
| Data pack layout/format | `python3 tools/backport/data_pack.py core/src/main/resources` |

The hand-written runtime shims live in `api/src/main/java/li/cil/oc/compat/`:

- `RegistryLookup` – registry access for code paths where 1.20.1 does not pass a `HolderLookup.Provider`.
- `CustomData` – 1.21 `minecraft:custom_data` semantics on top of the 1.20.1 item tag; hides vanilla-owned keys.
- `MathCompat`, `ModelCompat`, `NbtCompat` – Java 21 `Math.clamp`, packed-ARGB model tinting, size-limited compressed NBT reads.
