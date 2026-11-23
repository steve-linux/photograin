# SPDX-License-Identifier: GPL-3.0-or-later
# Extracts the shader sources from Shaders.kt, expanding $NAME templates, into a JSON file.
import re,sys,json
s=open(sys.argv[1]).read()
consts=dict(re.findall(r'const val (\w+) = """(.*?)"""', s, re.S))
def expand(t): return re.sub(r'\$(\w+)', lambda m: consts[m.group(1)], t)
json.dump({k:expand(v) for k,v in consts.items()}, open(sys.argv[2],'w'))
