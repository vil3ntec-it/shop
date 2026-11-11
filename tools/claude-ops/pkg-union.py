import sys,json,re
p=sys.argv[1]; L=open(p,encoding='utf-8').read().split('\n'); out=[]; i=0
while i<len(L):
  if L[i].startswith('<<<<<<< '):
    o=[];t=[];i+=1
    while not L[i].startswith('======='): o.append(L[i]); i+=1
    i+=1
    while not L[i].startswith('>>>>>>> '): t.append(L[i]); i+=1
    i+=1
    assert len(o)==len(t), (o,t)
    for a,b in zip(o,t):
      ka=re.match(r'\s*"([^"]+)":',a).group(1); kb=re.match(r'\s*"([^"]+)":',b).group(1); assert ka==kb,(ka,kb)
      pre=a[:a.index('": "')+4]; end=a[a.rindex('"'):]; assert b.startswith(pre) and b[b.rindex('"'):]==end
      oc=a[len(pre):a.rindex('"')].split(' && '); tc=b[len(pre):b.rindex('"')].split(' && ')
      out.append(pre+' && '.join(oc+[c for c in tc if c not in oc])+end)
  else: out.append(L[i]); i+=1
open(p,'w',encoding='utf-8').write('\n'.join(out)); json.load(open(p,encoding='utf-8')); print('ok')
