# NeoForge 21 -> Forge 47 package/class renames that are 1:1. Everything else (tick events,
# capabilities, networking, DeferredHolder, TriState, ...) is ported by hand.
# Usage: python3 neoforge_to_forge.py <dir>...
import re, sys, pathlib

# Specific renames first (order matters), then package prefixes.
RENAMES = [
    ('net.neoforged.neoforge.common.NeoForgeMod', 'net.minecraftforge.common.ForgeMod'),
    ('net.neoforged.neoforge.common.NeoForge', 'net.minecraftforge.common.MinecraftForge'),
    ('net.neoforged.neoforge.common.CommonHooks', 'net.minecraftforge.common.ForgeHooks'),
    ('net.neoforged.neoforge.client.ClientHooks', 'net.minecraftforge.client.ForgeHooksClient'),
    ('net.neoforged.neoforge.common.extensions.IMenuTypeExtension', 'net.minecraftforge.common.extensions.IForgeMenuType'),
    ('net.neoforged.neoforge.registries.DeferredHolder', 'net.minecraftforge.registries.RegistryObject'),
    ('net.neoforged.neoforge.registries.NeoForgeRegistries', 'net.minecraftforge.registries.ForgeRegistries'),
    ('net.neoforged.neoforge.common.conditions.ICondition', 'net.minecraftforge.common.crafting.conditions.ICondition'),
    ('net.neoforged.bus.api.', 'net.minecraftforge.eventbus.api.'),
    ('net.neoforged.api.distmarker.', 'net.minecraftforge.api.distmarker.'),
    ('net.neoforged.fml.', 'net.minecraftforge.fml.'),
    ('net.neoforged.neoforgespi.', 'net.minecraftforge.forgespi.'),
]
# Package prefixes that exist 1:1 under net.minecraftforge.
PREFIXES = ['fluids', 'registries', 'client.model', 'client.event', 'client.extensions', 'client.ChunkRenderTypeSet',
            'event.level', 'event.entity', 'event.server', 'event.RegisterCommandsEvent', 'energy', 'items',
            'server', 'common.util', 'common.loot']
# Simple class names used unqualified after the import rename.
SIMPLE = [(r'\bNeoForge\.EVENT_BUS\b', 'MinecraftForge.EVENT_BUS'), (r'\bNeoForgeMod\.', 'ForgeMod.'),
          (r'\bCommonHooks\.', 'ForgeHooks.'), (r'\bClientHooks\.', 'ForgeHooksClient.'),
          (r'\bIMenuTypeExtension\b', 'IForgeMenuType'), (r'\bNeoForgeRegistries\b', 'ForgeRegistries')]

tot = 0
for d in sys.argv[1:]:
    for p in pathlib.Path(d).rglob('*.java'):
        s = p.read_text(); o = s
        for a, b in RENAMES:
            s = s.replace(a, b)
        for pre in PREFIXES:
            s = s.replace('net.neoforged.neoforge.' + pre, 'net.minecraftforge.' + pre)
        if s != o:
            for a, b in SIMPLE:
                s = re.sub(a, b, s)
            p.write_text(s); tot += 1
print(tot, 'files')
