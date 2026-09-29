# Convert Java 21 pattern-matching switches to Java 17 if/instanceof chains.
# Usage: python3 pattern_switch.py <java-file>...
import re,sys,pathlib
def match_brace(s,i,o='{',c='}'):
    d=0; j=i
    while True:
        ch=s[j]
        if ch=='"':
            j+=1
            while s[j]!='"':
                if s[j]=='\\': j+=1
                j+=1
        elif ch=="'":
            j+=1
            while s[j]!="'":
                if s[j]=='\\': j+=1
                j+=1
        elif ch=='/' and s[j+1]=='/':
            j=s.index('\n',j)
        elif ch in '({[': d+=1
        elif ch in ')}]':
            d-=1
            if d==0: return j
        j+=1
def stmt_end(s,i):
    # end index (inclusive) of a statement/expression terminated by ';' at depth 0
    d=0; j=i
    while True:
        ch=s[j]
        if ch=='"':
            j+=1
            while s[j]!='"':
                if s[j]=='\\': j+=1
                j+=1
        elif ch in '({[': d+=1
        elif ch in ')}]': d-=1
        elif ch==';' and d==0: return j
        j+=1
def parse_cases(body):
    cases=[]; i=0
    while True:
        m=re.compile(r'\s*(case\s+(.*?)|default)\s*->\s*',re.S).match(body,i)
        if not m:
            assert body[i:].strip()=='', body[i:i+200]; return cases
        label=m.group(2) if m.group(1).startswith('case') else 'default'
        j=m.end()
        if body[j]=='{':
            e=match_brace(body,j); cases.append((label.strip(),'block',body[j+1:e])); i=e+1
        else:
            e=stmt_end(body,j); cases.append((label.strip(),'stmt',body[j:e+1])); i=e+1
def cond_for(label,var):
    label=' '.join(label.split())
    if label=='null': return f'{var} == null'
    guard=None
    if ' when ' in label: label,guard=label.split(' when ',1)
    m=re.match(r'^(.*\S)\s+([A-Za-z_]\w*)$',label); assert m,label
    typ,name=m.group(1),m.group(2)
    c=f'{var} instanceof {typ}' if name in('ignored','_') else f'{var} instanceof {typ} {name}'
    if guard: c+=f' && ({guard.strip()})'
    return c
def convert(s,expr_mode,var,body,indent):
    cases=parse_cases(body)
    out=[]; default=None; first=True
    for label,kind,content in cases:
        labels=[l.strip() for l in label.split(',')] if label.startswith('null') else [label]
        is_default='default' in labels
        conds=[cond_for(l,var) for l in labels if l!='default']
        if kind=='stmt':
            content=content.strip()
            if expr_mode and not content.startswith('throw'): content='return '+content
            blk=f'{{\n{indent}  {content}\n{indent}}}'
        else:
            if expr_mode:
                assert 'yield' not in re.sub(r'\byield\s','',content) or True
                content=re.sub(r'\byield\s',r'return ',content)
            assert not re.search(r'\bbreak\s*;',content), content
            content='\n'.join(l[2:] if l.startswith('  ') else l for l in content.split('\n'))
            blk='{'+content+'}'
        if is_default:
            default=(conds,blk); continue
        out.append((' || '.join(conds),blk))
    res=''
    for k,(c,b) in enumerate(out):
        res+=('if' if k==0 else ' else if')+f' ({c}) '+b
    if default:
        conds,b=default
        if conds:  # case null, default
            res+=(' else ' if res else '')+b
        elif b.strip('{} \n'):
            res+=(' else ' if res else '')+b
    return res
PAT=re.compile(r'(return\s+)?switch\s*\((\w+)\)\s*\{')
def process(s):
    n=0; pos=0
    while True:
        m=PAT.search(s,pos)
        if not m: return s,n
        bo=m.end()-1; bc=match_brace(s,bo); body=s[bo+1:bc]
        first=re.match(r'\s*case\s+([^-]*?)\s*->',body)
        if not first or not (re.match(r'^(null|[\w.<>?, \[\]]+\s+\w+)',first.group(1)) and (first.group(1).strip()=='null' or re.search(r'[A-Z][\w.<>?, \[\]]*\s+\w+$',first.group(1).split(' when ')[0]))):
            pos=m.end(); continue
        expr_mode=bool(m.group(1))
        line_start=s.rfind('\n',0,m.start())+1
        indent=re.match(r'\s*',s[line_start:]).group(0)
        end=bc
        if expr_mode:
            assert s[bc+1]==';'; end=bc+1
        s=s[:m.start()]+convert(s,expr_mode,m.group(2),body,indent)+s[end+1:]
        n+=1
for f in sys.argv[1:]:
    p=pathlib.Path(f); s=p.read_text(); t,n=process(s)
    if n: p.write_text(t); print(f,n)
