import re,sys,os
bad=re.compile(r'[.;\[\]/<>:\\]')
tot=0
for root in sys.argv[1:]:
  for d,_,fs in os.walk(root):
    if '/build' in d: continue
    for f in fs:
      if not f.endswith('.kt'): continue
      p=os.path.join(d,f)
      for i,l in enumerate(open(p,encoding='utf-8'),1):
        for m in re.finditer(r'fun\s+`([^`]*)`',l):
          if bad.search(m.group(1)): tot+=1; print(f'{p}:{i}: {m.group(1)[:80]}')
print('total',tot)
