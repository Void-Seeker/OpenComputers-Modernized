# ResourceLocation.fromNamespaceAndPath/parse/withDefaultNamespace(...) -> new ResourceLocation(...).
# Usage: python3 resource_location.py <dir-or-java-file>...
import re,sys,pathlib
pat=re.compile(r'((?:net\.minecraft\.resources\.)?ResourceLocation)\.(fromNamespaceAndPath|parse|withDefaultNamespace)\(')
n=0
for d in sys.argv[1:]:
  for p in ([pathlib.Path(d)] if d.endswith('.java') else pathlib.Path(d).rglob('*.java')):
    s=p.read_text(); t,k=pat.subn(r'new \1(',s)
    if k: p.write_text(t); n+=k
print(n)
