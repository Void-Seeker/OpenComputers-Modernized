# ItemStack.parseOptional(p, t) / ItemStack.parse(p, t).orElse(EMPTY) -> ItemStack.of(t);
# x.save(provider, tag) -> x.save(tag); x.save(provider) -> x.save(new CompoundTag()).
# Usage: python3 item_stack_nbt.py <dir-or-java-file>...
import re,sys,pathlib
def split_args(s,i):
    # s[i] is just after '(' ; return (args list, index after ')')
    depth=0; args=[]; cur=i; j=i
    while True:
        c=s[j]
        if c in '([{': depth+=1
        elif c in ')]}':
            if depth==0: args.append(s[cur:j].strip()); return args,j+1
            depth-=1
        elif c==',' and depth==0: args.append(s[cur:j].strip()); cur=j+1
        elif c=='"':
            j+=1
            while s[j]!='"':
                if s[j]=='\\': j+=1
                j+=1
        j+=1
PROV=re.compile(r'^(provider|registries|lookup|getEffectiveProvider\(\)|[\w.()]*registryAccess\(\))$')
def rewrite(s):
    n=0
    # ItemStack.parseOptional(p, t) -> ItemStack.of(t)
    while True:
        m=re.search(r'ItemStack\.parseOptional\(',s)
        if not m: break
        args,end=split_args(s,m.end()); assert len(args)==2 and PROV.match(args[0]),args
        s=s[:m.start()]+f'ItemStack.of({args[1]})'+s[end:]; n+=1
    # ItemStack.parse(p, t).orElse(ItemStack.EMPTY) -> ItemStack.of(t)
    while True:
        m=re.search(r'ItemStack\.parse\(',s)
        if not m: break
        args,end=split_args(s,m.end()); assert len(args)==2 and PROV.match(args[0]),args
        rest=s[end:]; mm=re.match(r'\.orElse\(ItemStack\.EMPTY\)',rest); assert mm
        s=s[:m.start()]+f'ItemStack.of({args[1]})'+rest[mm.end():]; n+=1
    # x.save(provider, tag) -> x.save(tag); x.save(provider) -> x.save(new CompoundTag())
    pos=0
    while True:
        m=re.compile(r'\.save\(').search(s,pos)
        if not m: break
        args,end=split_args(s,m.end())
        if len(args)==2 and PROV.match(args[0]):
            s=s[:m.end()]+args[1]+')'+s[end:]; n+=1
        elif len(args)==1 and PROV.match(args[0]):
            s=s[:m.end()]+'new CompoundTag())'+s[end:]; n+=1
        pos=m.end()
    return s,n
tot=0
for d in sys.argv[1:]:
  for p in ([pathlib.Path(d)] if d.endswith('.java') else pathlib.Path(d).rglob('*.java')):
    s=p.read_text(); t,k=rewrite(s)
    if k:
      if 'new CompoundTag()' in t and 'import net.minecraft.nbt.CompoundTag;' not in t and 'import net.minecraft.nbt.*;' not in t:
        t=re.sub(r'(\npackage [^;]+;\n)',r'\1\nimport net.minecraft.nbt.CompoundTag;\n',t,1)
      p.write_text(t); tot+=k; print(p,k)
print('total',tot)
