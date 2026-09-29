# Convert 1.21.1 data pack files to 1.20.1 (Forge) layout and format.
# Usage (from the repo, needs git): python3 data_pack.py <module>/src/main/resources
import json, os, re, sys, subprocess, collections
root = sys.argv[1]  # .../src/main/resources
data = os.path.join(root, 'data')
stats = collections.Counter()

def git_mv(a, b):
    os.makedirs(os.path.dirname(b), exist_ok=True)
    subprocess.check_call(['git', 'mv', a, b], cwd=root)

# 1) folder renames (singular 1.21 names -> plural 1.20.1 names)
REN = {'recipe': 'recipes', 'loot_table': 'loot_tables', 'advancement': 'advancements'}
TAGREN = {'item': 'items', 'block': 'blocks', 'fluid': 'fluids', 'entity_type': 'entity_types', 'game_event': 'game_events'}
for ns in os.listdir(data):
    for old, new in REN.items():
        p = os.path.join(data, ns, old)
        if os.path.isdir(p): git_mv(os.path.relpath(p, root), os.path.relpath(os.path.join(data, ns, new), root)); stats['dir ' + old] += 1
    tags = os.path.join(data, ns, 'tags')
    if os.path.isdir(tags):
        for old, new in TAGREN.items():
            p = os.path.join(tags, old)
            if os.path.isdir(p): git_mv(os.path.relpath(p, root), os.path.relpath(os.path.join(tags, new), root)); stats['tags/' + old] += 1

# 2) c: tags -> forge: tags
TAGMAP = {'c:glass_blocks': 'forge:glass'}
def forge_tag(t):
    return TAGMAP.get(t, 'forge:' + t[2:] if t.startswith('c:') else t)

def component_nbt(components):
    nbt = {}
    for k, v in components.items():
        if k == 'minecraft:custom_data': nbt.update(v)
        elif k == 'minecraft:dyed_color': nbt.setdefault('display', {})['color'] = v['rgb']
        else: raise ValueError('unhandled component ' + k)
    return nbt

def convert_result(r, smelting=False):
    if isinstance(r, dict) and 'id' in r:
        r = dict(r); r['item'] = r.pop('id')
        if 'components' in r: r['nbt'] = component_nbt(r.pop('components')); stats['result nbt'] += 1
        if smelting and set(r) == {'item'}: return r['item']
    return r

def walk_tags(o):
    if isinstance(o, dict):
        if isinstance(o.get('tag'), str) and o['tag'].startswith('c:'): o['tag'] = forge_tag(o['tag']); stats['c: tag'] += 1
        for v in o.values(): walk_tags(v)
    elif isinstance(o, list):
        for v in o: walk_tags(v)

def dump(path, o):
    with open(path, 'w') as f: json.dump(o, f, indent=2, ensure_ascii=False); f.write('\n')

for ns in os.listdir(data):
    rd = os.path.join(data, ns, 'recipes')
    if os.path.isdir(rd):
        for fn in sorted(os.listdir(rd)):
            p = os.path.join(rd, fn); o = json.load(open(p))
            if 'result' in o: o['result'] = convert_result(o['result'], smelting=o.get('type', '').endswith(('smelting', 'blasting', 'smoking', 'campfire_cooking')))
            if 'neoforge:conditions' in o: o['conditions'] = o.pop('neoforge:conditions'); stats['conditions'] += 1
            walk_tags(o); dump(p, o)
    ad = os.path.join(data, ns, 'advancements')
    if os.path.isdir(ad):
        for fn in sorted(os.listdir(ad)):
            p = os.path.join(ad, fn); o = json.load(open(p))
            icon = o.get('display', {}).get('icon')
            if icon and 'id' in icon: icon['item'] = icon.pop('id'); stats['icon'] += 1
            for crit in o.get('criteria', {}).values():
                for pred in crit.get('conditions', {}).get('items', []) or []:
                    comps = pred.pop('components', None)
                    if comps:
                        nbt = component_nbt(comps)
                        pred['nbt'] = json.dumps(nbt, separators=(',', ':')); stats['adv nbt'] += 1
            dump(p, o)
    # tags that also exist as forge: conventions
    ct = os.path.join(data, 'c', 'tags')
print(dict(stats))
