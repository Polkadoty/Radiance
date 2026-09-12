"""Stage a Java-only hybrid update using known installed native/companion binaries.

Does not install anything. Existing native DLL must match the new main JAR.
Companion bytecode is preserved; only its exact Radiance dependency is updated.
"""
from pathlib import Path
import argparse, hashlib, json, re, shutil, zipfile

p=argparse.ArgumentParser()
p.add_argument('--main-jar', type=Path, required=True)
p.add_argument('--game', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a=p.parse_args()
sha=lambda data:hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(a.main_jar) as z:
    meta=z.read('META-INF/neoforge.mods.toml').decode()
    version=re.search(r'^version\s*=\s*"([^"]+)"', meta, re.M).group(1)
    assert sha(z.read('core.dll'))==sha((a.game/'radiance/core.dll').read_bytes()), 'Native runtime mismatch'
assert not a.output.exists(), 'Use a new stage directory to preserve earlier checkpoints'
(a.output/'mods').mkdir(parents=True)
entries=[]
names=['radiance-hybrid-foundation-0.0.2-native-hud.2.jar',
       'radiance-dh-bridge-0.0.8-neoforge-1.21.1.jar',
       'radiance-sable-bridge-0.0.1-neoforge-1.21.1.jar']
old=list((a.game/'mods').glob('Radiance-0.*-neoforge-1.21.1.jar'))
assert len(old)==1, 'Expected exactly one active Radiance main JAR'
destination=a.output/'mods'/a.main_jar.name
shutil.copy2(a.main_jar,destination)
entries.append({'previous':old[0].name,'name':destination.name,
    'previousSha256':sha(old[0].read_bytes()),'sha256':sha(destination.read_bytes())})
for name in names:
    source=a.game/'mods'/name
    dest=a.output/'mods'/name
    with zipfile.ZipFile(source) as zi, zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as zo:
        assert not any(re.match(r'META-INF/[^/]+\.(SF|RSA|DSA)$',n,re.I) for n in zi.namelist())
        for info in zi.infolist():
            data=zi.read(info.filename)
            if info.filename=='META-INF/neoforge.mods.toml':
                data,n=re.subn(rb'\[0\.1\.5-alpha-port\.1-hybrid\.\d+\]',
                    ('['+version+']').encode(),data)
                assert n==1, 'Expected one pinned Radiance dependency'
            zo.writestr(info,data)
    with zipfile.ZipFile(source) as zi, zipfile.ZipFile(dest) as zo:
        changed=[n for n in zi.namelist() if zi.read(n)!=zo.read(n)]
        assert changed in ([], ['META-INF/neoforge.mods.toml']), changed
    entries.append({'previous':name,'name':name,'previousSha256':sha(source.read_bytes()),'sha256':sha(dest.read_bytes())})
(a.output/'manifest.json').write_text(json.dumps({'version':version,'game':str(a.game.resolve()),
    'nativeUnchanged':True,'companionChanges':'dependency metadata only','files':entries},indent=2)+'\n')
print(json.dumps({'version':version,'files':len(entries),'nativeUnchanged':True}))
