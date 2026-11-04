"""Derives the field-level config of a service from its coarse one: listener payload parameters
stop being whole-object sources; event record fields become sources and event constructor
arguments become sinks.  usage: mk_field_config.py <coarse.yml> <out.yml>"""
import re
import sys

src, out = sys.argv[1:3]
s = open(src, encoding='utf-8').read()
lines = [l for l in s.split('\n')
         if not re.search(r'kind: param, method: "<com\.shop\.\w+\.messaging\.consumer\.', l)
         and '# AMQP listener' not in l]
s = '\n'.join(lines)
s = s.replace('\nsinks:\n', '\n' + open('event-sources.yml', encoding='utf-8').read()
              + '\nsinks:\n' + open('event-sinks.yml', encoding='utf-8').read(), 1)
open(out, 'w', encoding='utf-8').write(s)
