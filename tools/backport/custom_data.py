# x.get/set/has/remove(DataComponents.CUSTOM_DATA[, v]) -> li.cil.oc.compat.CustomData.get/set/has/remove(x[, v]).
# Run after normalizing fully qualified net.minecraft.core.component.DataComponents.CUSTOM_DATA to DataComponents.CUSTOM_DATA.
# Usage: python3 custom_data.py <dir-or-java-file>...
import re,sys,pathlib
CALL=re.compile(r'\.(get|set|has|remove|getOrDefault)\(DataComponents\.CUSTOM_DATA\s*(,\s*)?')
def receiver_start(s,i):
    # walk back from index i (the '.') over a primary expression: idents, dots, balanced () [] and generic-free calls
    j=i
    while True:
        j-=1
        while j>=0 and s[j] in ' \t\n': j-=1
        if j<0: return 0
        c=s[j]
        if c in ')]':
            op={')':'(',']':'['}[c]; depth=0
            while j>=0:
                if s[j]==c: depth+=1
                elif s[j]==op:
                    depth-=1
                    if depth==0: break
                j-=1
            # continue to include callee name
            k=j-1
            while k>=0 and (s[k].isalnum() or s[k] in '_$'): k-=1
            j=k+1
            if j>0 and s[j-1]=='.': j-=1; continue
            return j
        elif c.isalnum() or c in '_$':
            while j>=0 and (s[j].isalnum() or s[j] in '_$'): j-=1
            j+=1
            if j>0 and s[j-1]=='.': j-=1; continue
            return j
        else:
            return j+1
def fix(s):
    out=0
    while True:
        m=CALL.search(s)
        if not m: return s,out
        st=receiver_start(s,m.start())
        recv=s[st:m.start()].strip()
        name=m.group(1)
        if name=='getOrDefault':
            # getOrDefault(CUSTOM_DATA, CustomData.EMPTY) -> CustomData.getOrEmpty(recv)
            rest=s[m.end():]
            mm=re.match(r'CustomData\.EMPTY\s*\)',rest)
            assert mm, s[st:m.end()+60]
            s=s[:st]+f'CustomData.getOrEmpty({recv})'+rest[mm.end():]
        elif m.group(2):
            s=s[:st]+f'CustomData.{name}({recv}, '+s[m.end():]
        else:
            rest=s[m.end():]; assert rest.startswith(')'), s[st:m.end()+40]
            s=s[:st]+f'CustomData.{name}({recv})'+rest[1:]
        out+=1
n=0
for d in sys.argv[1:]:
  for p in ([pathlib.Path(d)] if d.endswith('.java') else pathlib.Path(d).rglob('*.java')):
    s=p.read_text()
    if 'DataComponents.CUSTOM_DATA' not in s and 'CustomData' not in s: continue
    t,k=fix(s)
    t=t.replace('CustomData.set(DataComponents.CUSTOM_DATA, ','CustomData.update(')  # fixed up by hand below if needed
    t=t.replace('import net.minecraft.world.item.component.CustomData;','import li.cil.oc.compat.CustomData;')
    t=re.sub(r'\bnet\.minecraft\.world\.item\.component\.CustomData\b','li.cil.oc.compat.CustomData',t)
    if 'CustomData.' in t and 'import li.cil.oc.compat.CustomData;' not in t and 'li.cil.oc.compat.CustomData' not in t and 'package li.cil.oc.compat;' not in t:
        t=re.sub(r'(?m)^(package [^;]+;\n)',r'\1\nimport li.cil.oc.compat.CustomData;\n',t,1)
    if t!=s: p.write_text(t); n+=k; print(p,k)
print('total',n)
