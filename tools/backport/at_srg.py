# Prints Forge 1.20.1 access transformer lines (SRG names) for official-name members.
# Input lines on stdin: "<access> <binary.class.Name> <fieldName>" or "<access> <class> <method>(<desc>)".
# Needs the mapping MDG writes: forge/build/moddev/artifacts/namedToIntermediate.tsrg
# Usage: echo "public net.minecraft.client.renderer.RenderStateShard NO_CULL" | python3 at_srg.py <tsrg>
import sys

fields, methods, cur = {}, {}, None
for line in open(sys.argv[1]):
    if not line.startswith('\t'):
        cur = line.split()[0]; continue
    parts = line.split()
    if len(parts) == 2: fields[(cur, parts[0])] = parts[1]
    elif len(parts) == 3: methods[(cur, parts[0] + parts[1])] = (parts[2], parts[1])
for line in sys.stdin:
    if not line.strip(): continue
    acc, cls, member = line.split()
    key = cls.replace('.', '/')
    if '(' in member:
        name = member[:member.index('(')]
        srg = methods.get((key, member))
        print(f'{acc} {cls} {srg[0]}{srg[1]} # {name}' if srg else f'# UNMAPPED {line.strip()}')
    else:
        srg = fields.get((key, member))
        print(f'{acc} {cls} {srg} # {member}' if srg else f'# UNMAPPED {line.strip()}')
