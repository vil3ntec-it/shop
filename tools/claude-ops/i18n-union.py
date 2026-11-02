import re,sys
p=sys.argv[1]
s=open(p,encoding='utf-8').read()
def fix(m):
    o,t=m.group(1),m.group(2)
    ot=re.findall(r'\b\w+\b',o); tt=re.findall(r'\b\w+\b',t)
    extra=[x for x in tt if x not in ot]
    if not extra: return o
    lines=o.rstrip('\n').split('\n')
    lines[-1]=lines[-1].rstrip()
    ind=re.match(r'\s*',lines[-1]).group(0)
    if '//' in lines[-1]:
        lines.append(ind+', '.join(extra)+',')   # شرحِ پایانِ سطر: نام‌ها در سطرِ تازه، نه درونِ شرح
    else:
        if not lines[-1].endswith(','): lines[-1]+=','
        lines[-1]+=' '+', '.join(extra)+','
    return '\n'.join(lines)+'\n'
s=re.sub(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n',fix,s,flags=re.S)
open(p,'w',encoding='utf-8').write(s)
