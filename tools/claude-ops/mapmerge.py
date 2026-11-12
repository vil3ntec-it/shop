# mapmerge.py <wt> <their-ref>: 3-way merge of PARTIAL-MAP by row: start from OURS (HEAD), apply every row THEIRS changed vs merge-base
# (text or section, add or delete), add THEIRS' new round-note paragraphs, then mapfix.py (drop non-PARTIAL, recount).
import re,sys,os,subprocess
wt,ref=sys.argv[1],sys.argv[2]; os.chdir(wt)
P='docs/spec/PARTIAL-MAP-fa.md'
g=lambda *a: subprocess.run(['git',*a],capture_output=True,text=True).stdout
base=g('merge-base','HEAD',ref).strip()
show=lambda r: g('show',f'{r}:{P}')
def rows(t):
    d={};sec=None
    for l in t.split('\n'):
        if l.startswith('## '): sec=l.split(' (')[0]
        c=l.split('|')
        if sec and l.startswith('| ') and len(c)>3 and re.match(r'^[A-Z0-9][A-Za-z0-9@-]*$',c[1].strip()) and c[1].strip()!='CI': d[c[1].strip()]=(sec,l)
    return d
B,O,T=rows(show(base)),rows(show('HEAD')),rows(show(ref))
ours=show('HEAD').split('\n')
changed={k for k in set(B)|set(T) if B.get(k)!=T.get(k)}
out=[l for l in ours if not (l.startswith('| ') and l.split('|')[1].strip() in changed)]
for k in sorted(changed):
    if k not in T: continue
    sec,line=T[k]
    i=next(i for i,l in enumerate(out) if l.startswith(sec+' ('))
    j=next(j for j in range(i,len(out)) if out[j].startswith('|---'))
    out.insert(j+1,line)
bp=set(show(base).split('\n\n')); tp=[p for p in show(ref).split('\n\n') if p.startswith('**') and p not in bp and not p.startswith('**همه:') and p not in '\n\n'.join(out)]
k=next(i for i,l in enumerate(out) if l.startswith('**همه:'))
for p in tp: out.insert(k,p+'\n')
open(P,'w',encoding='utf-8').write('\n'.join(out))
print('theirs changed:',sorted(changed))
