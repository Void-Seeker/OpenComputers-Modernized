# NeoForge DeferredHolder<R, T> -> Forge RegistryObject<T> (type positions only).
# Usage: python3 deferred_holder.py <dir>...
import re, sys, pathlib

def split_generic(s, i):
    # s[i] is just after '<'; returns (args, index after matching '>')
    depth = 0; args = []; cur = i; j = i
    while True:
        c = s[j]
        if c in '<([': depth += 1
        elif c in ')]': depth -= 1
        elif c == '>':
            if depth == 0:
                args.append(s[cur:j].strip()); return args, j + 1
            depth -= 1
        elif c == ',' and depth == 0:
            args.append(s[cur:j].strip()); cur = j + 1
        j += 1

tot = 0
for d in sys.argv[1:]:
    for p in pathlib.Path(d).rglob('*.java'):
        s = p.read_text(); n = 0
        while True:
            m = re.search(r'\bDeferredHolder<', s)
            if not m: break
            args, end = split_generic(s, m.end())
            assert len(args) == 2, (p, args)
            value = f'? extends {args[0]}' if args[1] == '?' else args[1]  # DeferredHolder<R, T extends R>
            s = s[:m.start()] + f'RegistryObject<{value}>' + s[end:]; n += 1
        if n:
            if 'import net.minecraftforge.registries.RegistryObject;' not in s:
                s = re.sub(r'^(package [^;]+;\n)', r'\1\nimport net.minecraftforge.registries.RegistryObject;\n', s, count=1, flags=re.M)
            p.write_text(s); tot += n
print(tot)
