# NeoForge capability call sites -> li.cil.oc.neoforge.compat stand-ins:
#   level.getCapability(X.BLOCK, pos, side) -> X.BLOCK.getCapability(level, pos, side)
#   stack.getCapability(X.ITEM)             -> X.ITEM.getCapability(stack)
#   RegisterCapabilitiesEvent (NeoForge)    -> CapabilityRegistrar
# Usage: python3 capabilities.py <dir>...
import re, sys, pathlib

CAP = r'((?:[\w.]+\.)?(?:Capabilities\.\w+\.(?:BLOCK|ITEM)|OCBlockCapabilities\.[A-Z_]+))'
RECV = r'([A-Za-z_][\w]*(?:\(\))?(?:\.[A-Za-z_]\w*(?:\(\))?)*)'
BLOCK = re.compile(RECV + r'\.getCapability\(\s*' + CAP + r'\s*,\s*')
ITEM = re.compile(RECV + r'\.getCapability\(\s*' + CAP + r'\s*\)')

tot = 0
for d in sys.argv[1:]:
    for p in pathlib.Path(d).rglob('*.java'):
        s = p.read_text(); o = s
        s = s.replace('net.neoforged.neoforge.capabilities.Capabilities', 'li.cil.oc.neoforge.compat.Capabilities')
        s = s.replace('net.neoforged.neoforge.capabilities.BlockCapability', 'li.cil.oc.neoforge.compat.BlockCapability')
        s = s.replace('net.neoforged.neoforge.capabilities.ItemCapability', 'li.cil.oc.neoforge.compat.ItemCapability')
        if 'net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent' in s:
            s = s.replace('net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent', 'li.cil.oc.neoforge.compat.CapabilityRegistrar')
            s = re.sub(r'\bRegisterCapabilitiesEvent\b', 'CapabilityRegistrar', s)
        s = ITEM.sub(lambda m: f'{m.group(2)}.getCapability({m.group(1)})', s)
        s = BLOCK.sub(lambda m: f'{m.group(2)}.getCapability({m.group(1)}, ', s)
        if s != o:
            p.write_text(s); tot += 1
print(tot, 'files')
