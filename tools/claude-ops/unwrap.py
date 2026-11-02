# برداشتنِ wrap ِ محلی از درهای Express 5: «const wrap = …» حذف و هر wrap(X) ⇒ X
import re,sys
for p in sys.argv[1:]:
    s=open(p,encoding='utf-8').read()
    s=re.sub(r"^const wrap = \(fn\) => \(req, res, next\) => Promise\.resolve\(fn\(req, res, next\)\)\.catch\(next\);\n","",s,flags=re.M)
    out=[];i=0
    while True:
        j=s.find('wrap(',i)
        if j<0: out.append(s[i:]); break
        if j>0 and (s[j-1].isalnum() or s[j-1]=='_'): out.append(s[i:j+5]); i=j+5; continue
        out.append(s[i:j]); k=j+5; depth=1; m=k
        while depth:
            c=s[m]
            if c=='(': depth+=1
            elif c==')': depth-=1
            m+=1
        out.append(s[k:m-1]); i=m
    s=''.join(out)
    assert not re.search(r'(?<![A-Za-z_])wrap\(',s), p
    open(p,'w',encoding='utf-8').write(s); print('unwrapped',p)
