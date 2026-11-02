# Wrap $x / ${expr} in the FIRST string literal (fa) of t(...)/td(...) calls with ${asIs(...)}; skip already-wrapped.
import re,sys
def wrap_fa(lit):
    out=[];i=0
    while i<len(lit):
        if lit[i]=='$' and i+1<len(lit) and lit[i+1]=='{':
            depth=0;j=i+1
            while j<len(lit):
                if lit[j]=='{': depth+=1
                elif lit[j]=='}':
                    depth-=1
                    if depth==0: break
                j+=1
            expr=lit[i+2:j]
            out.append(lit[i:j+1] if expr.startswith('asIs(') else '${asIs('+expr+')}'); i=j+1; continue
        m=re.match(r'\$([A-Za-z_][A-Za-z0-9_]*)',lit[i:])
        if m:
            out.append('${asIs('+m.group(1)+')}'); i+=len(m.group(0)); continue
        out.append(lit[i]); i+=1
    return ''.join(out)
n=0
for p in sys.argv[1:]:
    s=open(p,encoding='utf-8').read(); res=[]; pos=0
    for m in re.finditer(r'\btd?\(\s*"',s):
        start=m.end()-1  # opening quote
        # find end of string literal (handle ${...} with nested quotes)
        j=start+1;depth=0
        while j<len(s):
            c=s[j]
            if c=='\\': j+=2; continue
            if c=='$' and s[j+1:j+2]=='{': depth+=1; j+=2; continue
            if depth and c=='}': depth-=1; j+=1; continue
            if c=='"' and depth==0: break
            j+=1
        lit=s[start+1:j]; w=wrap_fa(lit)
        if w!=lit: n+=1
        res.append(s[pos:start+1]); res.append(w); pos=j
    res.append(s[pos:]); open(p,'w',encoding='utf-8').write(''.join(res))
print('wrapped literals:',n)
