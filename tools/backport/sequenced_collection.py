# Java 21 List.getFirst/getLast/removeFirst/removeLast() -> Java 17 equivalents, applied only
# where javac reports "cannot find symbol" for them (so Deque receivers are left alone).
# Usage: ./gradlew :core:compileJava > err.txt 2>&1; python3 sequenced_collection.py err.txt
import re, sys

err = open(sys.argv[1]).read().split('\n')
fixes = {}
for k, l in enumerate(err):
    m = re.match(r'^(/\S+\.java):(\d+): error: cannot find symbol', l)
    if not m:
        continue
    sym = next((x for x in err[k + 1:k + 6] if 'symbol:' in x), '')
    mm = re.search(r'method (getFirst|getLast|removeFirst|removeLast)\(\)', sym)
    if mm:
        fixes.setdefault(m.group(1), []).append((int(m.group(2)), mm.group(1)))
for f, items in fixes.items():
    lines = open(f).read().split('\n')
    for ln, meth in items:
        rx = re.compile(r'([A-Za-z_][\w.]*(?:\(\))?)\.' + meth + r'\(\)')

        def rep(m):
            r = m.group(1)
            return {'getFirst': f'{r}.get(0)', 'removeFirst': f'{r}.remove(0)',
                    'getLast': f'{r}.get({r}.size() - 1)', 'removeLast': f'{r}.remove({r}.size() - 1)'}[meth]

        lines[ln - 1] = rx.sub(rep, lines[ln - 1], count=1)
    open(f, 'w').write('\n'.join(lines))
