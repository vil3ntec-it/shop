# mapfix.py <wt>: resolve PARTIAL-MAP header conflicts (keep all paragraphs, ours table), drop rows not PARTIAL in STATUS, recount
import re,sys,os
os.chdir(sys.argv[1])
p='docs/spec/PARTIAL-MAP-fa.md'
s=open(p,encoding='utf-8').read()
def blk(m):
    o,t=m.group(1),m.group(2)
    if '**همه:' in o and '**همه:' in t:
        po,pt=o.split('**همه:')[0],t.split('**همه:')[0]
        extra=pt if pt.strip() and pt.strip() not in po else ''
        return po+extra+'**همه:'+o.split('**همه:',1)[1]
    if o.startswith('## ') and t.startswith('## '): return o
    # row-level: union of lines
    ol=o.split('\n'); return o+''.join(l+'\n' for l in t.split('\n') if l and l not in ol)
s=re.sub(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n',blk,s,flags=re.S)
st=open('docs/spec/STATUS.md',encoding='utf-8').read().split('\n')
part=set()
for l in st:
    c=[x.strip() for x in l.split('|')]
    if len(c)>4 and re.match(r'^[A-Z0-9][A-Za-z0-9@-]*$',c[1] or '') and 'PARTIAL' in c: part.add(c[1])
out=[];seen=set();cur=None
for l in s.split('\n'):
    if l.startswith('## '): cur=l
    c=[x.strip() for x in l.split('|')]
    if cur and l.startswith('| ') and len(c)>3 and re.match(r'^[A-Z0-9][A-Za-z0-9@-]*$',c[1]) and c[1]!='CI':
        if c[1] not in part or c[1] in seen: continue
        seen.add(c[1])
    out.append(l)
s='\n'.join(out)
fa=str.maketrans('0123456789','۰۱۲۳۴۵۶۷۸۹');cnt={}
for sec in re.split(r'\n(?=## )',s):
    m=re.match(r'## (\S+(?:/\S+)?) \(',sec)
    if m: cnt[m.group(1)]=len([l for l in sec.split('\n') if l.startswith('| ') and not l.startswith('| شناسه') and not l.startswith('|---')])
for n,c in cnt.items():
    s=re.sub(r'## '+re.escape(n)+r' \([۰-۹]+\)',f'## {n} ({str(c).translate(fa)})',s)
    s=re.sub(r'(\| '+re.escape(n)+r' \| )[۰-۹]+( \|)',lambda m:m.group(1)+str(c).translate(fa)+m.group(2),s)
    s=re.sub(re.escape(n)+r': \*\*[۰-۹]+\*\*',f'{n}: **{str(c).translate(fa)}**',s)
s=re.sub(r'\*\*همه: [۰-۹]+ ردیف\.\*\*',f'**همه: {str(sum(cnt.values())).translate(fa)} ردیف.**',s)
open(p,'w',encoding='utf-8').write(s)
miss=sorted(part-seen)
print(cnt,sum(cnt.values()),'status',len(part),'missing',miss, 'conflicts-left', s.count('<<<<<<<'))
