# list-union.py file: هر تعارض ⇒ سطرهای «ما»؛ سطرِ هم‌شکلِ آرایه/زنجیرهٔ && با عضوهای افزودهٔ «آن‌ها»؛ ورودی‌های PARTS (پیش از «};») به هم افزوده
import re,sys
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
def items(line):
    if '&&' in line and '"' in line: return [x.strip() for x in re.search(r'"([^"]*)"\s*:\s*"(.*)"',line).group(2).split('&&')]
    m=re.search(r'\[(.*)\]',line); return [x.strip() for x in m.group(1).split(',')] if m else None
def merge_line(o,t):
    io,it=items(o),items(t)
    if io is None or it is None: return o
    extra=[x for x in it if x not in io]
    if not extra: return o
    if '&&' in o:
        k,v=re.search(r'("[^"]*"\s*:\s*)"(.*)"',o).groups()
        return o.replace(v, v+' && '+' && '.join(extra),1)
    m=re.search(r'\[(.*)\]',o); inner=m.group(1)
    return o[:m.start(1)]+inner+', '+', '.join(extra)+o[m.end(1):]
def fix(m):
    O=m.group(1).rstrip('\n').split('\n'); T=m.group(2).rstrip('\n').split('\n')
    if '};' in O and '};' in T:
        oi,ti=O.index('};'),T.index('};')
        head=O[:oi]+T[:ti]; tailO=O[oi:]; tailT=T[ti:]
    else: head=[]; tailO=O; tailT=T
    out=[]
    for i,l in enumerate(tailO):
        tl=tailT[i] if i<len(tailT) else None
        out.append(merge_line(l,tl) if tl is not None and l!=tl else l)
    return '\n'.join(head+out)+'\n'
s=re.sub(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n',fix,s,flags=re.S)
open(p,'w',encoding='utf-8').write(s)
