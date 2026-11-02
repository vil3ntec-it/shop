# onew.py file: هر تعارض ⇒ سطرهای «ما» + سطرهای تازهٔ «آن‌ها» (نه هم‌سرِ یکی از «ما» در ۶۰ نویسهٔ نخست)
import re,sys
p=sys.argv[1]; s=open(p,encoding='utf-8').read()
def fix(m):
    O=m.group(1).split('\n')[:-1]; T=m.group(2).split('\n')[:-1]
    new=[t for t in T if t not in O and not any(t[:60]==o[:60] for o in O)]
    return '\n'.join(O+new)+'\n'
s=re.sub(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n',fix,s,flags=re.S)
open(p,'w',encoding='utf-8').write(s)
