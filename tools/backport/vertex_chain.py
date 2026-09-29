# 1.21 VertexConsumer chains -> 1.20.1: addVertex/setColor/setUv/setOverlay/setLight/setNormal
# -> vertex/color/uv/overlayCoords/uv2/normal, terminated with .endVertex().
# 1.20.1 silently drops elements written out of vertex-format order, so check chain order first
# (position, color, uv, overlay, light, normal).
# Usage: python3 vertex_chain.py <dir>...
import re, sys, pathlib

REN = {'addVertex': 'vertex', 'setColor': 'color', 'setUv': 'uv', 'setOverlay': 'overlayCoords', 'setLight': 'uv2', 'setNormal': 'normal'}
tot = 0
for d in sys.argv[1:]:
    for p in pathlib.Path(d).rglob('*.java'):
        s = p.read_text()
        if 'addVertex(' not in s:
            continue
        out = []; pos = 0; n = 0
        for m in re.finditer(r'\.addVertex\(', s):
            if m.start() < pos:
                continue
            e = s.index(';', m.start())
            seg = re.sub(r'\.(addVertex|setColor|setUv|setOverlay|setLight|setNormal)\(', lambda k: '.' + REN[k.group(1)] + '(', s[m.start():e])
            out.append(s[pos:m.start()]); out.append(seg + '.endVertex()'); pos = e; n += 1
        out.append(s[pos:]); p.write_text(''.join(out)); tot += n
print(tot)
