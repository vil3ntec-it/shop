import re,json,sys
f=sys.argv[1]; s=open(f).read()
pat=re.compile(r'<<<<<<< [^\n]*\n(.*?)(?:\|\|\|\|\|\|\| [^\n]*\n(.*?))?=======\n(.*?)>>>>>>> [^\n]*\n', re.S)
def mx(mm):
    o,b,t=mm.group(1),mm.group(2),mm.group(3)
    po=dict(re.findall(r'"([^"]+)": (\d+)',o)); pt=dict(re.findall(r'"([^"]+)": (\d+)',t))
    keys=[k for k,_ in re.findall(r'"([^"]+)": (\d+)',o)]
    for k in pt:
        if k not in keys: keys.append(k)
    return '\n'.join(f'    "{k}": {max(int(po.get(k,0)),int(pt.get(k,0)))},' for k in keys)+'\n'
s,n=pat.subn(mx,s)
s=re.sub(r',(\s*\n\s*[}\]])', r'\1', s)
s=re.sub(r'(": \d+)\n(\s+")', r'\1,\n\2', s)
json.loads(s); open(f,'w').write(s); print('baseline hunks',n)
