#!/usr/bin/env python3
"""cres.py file order...: هر تعارض به ترتیب با یکی از o (ما)، t (آن‌ها)، ot، to حل می‌شود"""
import sys,re
f=sys.argv[1]; orders=sys.argv[2:]
L=open(f).read().split('\n'); out=[]; i=0; k=0
while i<len(L):
  if L[i].startswith('<<<<<<< '):
    o=[];t=[];i+=1
    while not L[i].startswith('=======') and not L[i].startswith('||||||| '): o.append(L[i]); i+=1
    if L[i].startswith('||||||| '):
      while not L[i].startswith('======='): i+=1
    i+=1
    while not L[i].startswith('>>>>>>> '): t.append(L[i]); i+=1
    i+=1; m=orders[k]; k+=1
    out+= {'o':o,'t':t,'ot':o+t,'to':t+o}[m]
  else: out.append(L[i]); i+=1
assert k==len(orders),(k,len(orders))
open(f,'w').write('\n'.join(out))
