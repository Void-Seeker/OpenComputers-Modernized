"""Finds abstract methods that a concrete class in the reobfuscated (SRG) mod jar does not implement.

Reobfuscation renames a method of the mod's own interfaces to its SRG name when some implementing class
also inherits a Minecraft method with the same name and descriptor (e.g. EnvironmentHost.level() vs
Entity.level() -> m_9236_). Implementations in classes unrelated to that Minecraft class keep the old
name, and the game crashes with AbstractMethodError the first time the method is called.

    python3 tools/backport/check_reobf.py forge/build/libs/<mod>.jar \
        forge/build/moddev/artifacts/forge-*-merged.jar forge/build/moddev/artifacts/namedToIntermediate.tsrg
"""
import struct, sys, zipfile

ACC_INTERFACE, ACC_ABSTRACT, ACC_STATIC, ACC_PRIVATE, ACC_BRIDGE = 0x0200, 0x0400, 0x0008, 0x0002, 0x0040


def parse_class(data):
    def u1(o): return data[o]
    def u2(o): return struct.unpack_from('>H', data, o)[0]
    def u4(o): return struct.unpack_from('>I', data, o)[0]
    n = u2(8)
    cp = [None] * n
    o, i = 10, 1
    while i < n:
        tag = u1(o)
        if tag == 1:
            ln = u2(o + 1); cp[i] = ('utf8', data[o + 3:o + 3 + ln].decode('utf-8', 'replace')); o += 3 + ln
        elif tag in (3, 4):
            o += 5
        elif tag in (5, 6):
            o += 9; i += 1
        elif tag == 7:
            cp[i] = ('class', u2(o + 1)); o += 3
        elif tag in (8, 16, 19, 20):
            o += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            o += 5
        elif tag == 15:
            o += 4
        else:
            raise ValueError('cp tag %d' % tag)
        i += 1
    def cls(idx): return cp[cp[idx][1]][1] if idx else None
    access = u2(o); this = cls(u2(o + 2)); sup = cls(u2(o + 4))
    ic = u2(o + 6); o += 8
    ifaces = [cls(u2(o + 2 * k)) for k in range(ic)]; o += 2 * ic
    for _ in range(2):  # fields, then methods
        cnt = u2(o); o += 2
        members = []
        for _ in range(cnt):
            acc, name, desc, ac = u2(o), cp[u2(o + 2)][1], cp[u2(o + 4)][1], u2(o + 6); o += 8
            for _ in range(ac):
                o += 6 + u4(o + 2)
            members.append((name, desc, acc))
    return {'name': this, 'super': sup, 'ifaces': ifaces, 'access': access, 'methods': members}


def load_jar(path, keep=lambda n: True):
    out = {}
    with zipfile.ZipFile(path) as z:
        for e in z.namelist():
            if e.endswith('.class') and keep(e):
                c = parse_class(z.read(e))
                out[c['name']] = c
    return out


def load_tsrg(path):
    """named class -> {(named method, named desc): srg method}"""
    maps, cur = {}, None
    for line in open(path):
        if not line.strip() or line.startswith('tsrg2'):
            continue
        if not line.startswith('\t'):
            cur = maps.setdefault(line.split()[0], {})
        elif not line.startswith('\t\t'):
            parts = line.split()
            if len(parts) == 3:  # method: name desc srg
                cur[(parts[0], parts[1])] = parts[2]
    return maps


def main(mod_jar, mc_jar, tsrg):
    mod = load_jar(mod_jar)
    mc = load_jar(mc_jar, lambda e: e.startswith(('net/minecraft/', 'com/mojang/', 'net/minecraftforge/')))
    maps = load_tsrg(tsrg)
    def srg_name(name, n, d, seen=None):
        """SRG name of method n+d declared in or inherited into class `name` (Forge classes inherit theirs)."""
        seen = set() if seen is None else seen
        if name in seen or name not in mc:
            return None
        seen.add(name)
        hit = maps.get(name, {}).get((n, d))
        if hit:
            return hit
        c = mc[name]
        for parent in [c['super']] + c['ifaces']:
            hit = parent and srg_name(parent, n, d, seen)
            if hit:
                return hit
        return None

    renamed = {}
    for name, c in mc.items():  # Minecraft/Forge classes as they look at runtime (SRG method names)
        renamed[name] = [(srg_name(name, n, d) or n, d, a) for n, d, a in c['methods']]
    for name, methods in renamed.items():
        mc[name]['methods'] = methods
    classes = {**mc, **mod}

    def supers(name):
        c = classes.get(name)
        while c:
            yield c
            c = classes.get(c['super'])

    def all_ifaces(name, seen=None):
        seen = set() if seen is None else seen
        c = classes.get(name)
        if not c:
            return seen
        for i in c['ifaces'] + ([c['super']] if c['super'] else []):
            if i and i not in seen:
                if classes.get(i, {}).get('access', 0) & ACC_INTERFACE:
                    seen.add(i)
                all_ifaces(i, seen)
        return seen

    def implemented(name, mname, desc):
        for c in supers(name):
            for n, d, a in c['methods']:
                if n == mname and d == desc and not a & (ACC_ABSTRACT | ACC_STATIC):
                    return True
        for i in all_ifaces(name):  # default methods
            for n, d, a in classes[i]['methods']:
                if n == mname and d == desc and not a & (ACC_ABSTRACT | ACC_STATIC):
                    return True
        return False

    unknown_supers, problems = set(), []
    for name, c in mod.items():
        if c['access'] & (ACC_INTERFACE | ACC_ABSTRACT):
            continue
        chain = list(supers(name))
        if chain[-1]['super'] not in (None, 'java/lang/Object') and chain[-1]['super'] not in classes:
            unknown_supers.add(chain[-1]['super'])
        needed = set()
        for i in all_ifaces(name):
            needed |= {(n, d, i) for n, d, a in classes[i]['methods'] if a & ACC_ABSTRACT}
        for s in chain:
            if s['access'] & ACC_ABSTRACT:
                needed |= {(n, d, s['name']) for n, d, a in s['methods'] if a & ACC_ABSTRACT}
        for n, d, owner in sorted(needed):
            if not implemented(name, n, d):
                problems.append((name, owner, n, d))
    for p in problems:
        print('%s does not implement %s.%s%s' % p)
    print('%d problems in %d mod classes' % (len(problems), len(mod)))
    if unknown_supers:
        print('superclasses outside the checked jars (not verified):', ', '.join(sorted(unknown_supers))[:2000])


if __name__ == '__main__':
    main(*sys.argv[1:4])
