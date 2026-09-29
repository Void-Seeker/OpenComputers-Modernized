# NeoForge tick events -> Forge 47 TickEvent with phase:
#   void h(ClientTickEvent.Post e) { ... }  ->  void h(TickEvent.ClientTickEvent e) { if (e.phase != TickEvent.Phase.END) return; ... }
# Pre -> Phase.START, Post -> Phase.END. Same for Server/Player/Level tick events.
# Usage: python3 tick_events.py <dir>...
import re, sys, pathlib

KINDS = {'ClientTickEvent': 'ClientTickEvent', 'ServerTickEvent': 'ServerTickEvent',
         'PlayerTickEvent': 'PlayerTickEvent', 'LevelTickEvent': 'LevelTickEvent'}
PARAM = re.compile(r'\((?:final )?(?:[\w.]+\.)?(ClientTickEvent|ServerTickEvent|PlayerTickEvent|LevelTickEvent)\.(Pre|Post) (\w+)\)(\s*(?:throws [\w., ]+)?)\{')
IMPORTS = re.compile(r'import net\.(?:neoforged\.neoforge\.event\.tick|minecraftforge\.client\.event|neoforged\.neoforge\.client\.event)\.(ClientTickEvent|ServerTickEvent|PlayerTickEvent|LevelTickEvent);\n')

tot = 0
for d in sys.argv[1:]:
    for p in pathlib.Path(d).rglob('*.java'):
        s = p.read_text(); o = s
        def rep(m):
            kind, when, var, tail = m.groups()
            phase = 'START' if when == 'Pre' else 'END'
            return (f'(TickEvent.{KINDS[kind]} {var}){tail}{{\n'
                    f'    if ({var}.phase != TickEvent.Phase.{phase}) return;')
        s = PARAM.sub(rep, s)
        if s != o:
            s = IMPORTS.sub('', s)
            if 'import net.minecraftforge.event.TickEvent;' not in s:
                s = re.sub(r'^(package [^;]+;\n)', r'\1\nimport net.minecraftforge.event.TickEvent;\n', s, count=1, flags=re.M)
            p.write_text(s); tot += 1
print(tot, 'files')
