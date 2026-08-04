#!/usr/bin/env python3
import json,sys
path=sys.argv[1]
for i,line in enumerate(open(path,encoding='utf-8'),1):
    row=json.loads(line); assert isinstance(row.get('messages'),list), i
print('Dataset válido')
