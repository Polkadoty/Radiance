"""Build the hybrid companion against an existing matching main JAR and cached runtime dependencies."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

p = argparse.ArgumentParser()
for name in ('main-jar', 'libraries', 'compile-deps', 'jdk', 'output'):
    p.add_argument('--' + name, type=Path, required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
source = root / 'compat/hybrid'
assert not a.output.exists(), 'Choose a fresh output directory'
classes = a.output.resolve() / 'classes'
classes.mkdir(parents=True)
deps = [a.main_jar.resolve(), a.libraries / 'net/neoforged/neoforge/21.1.249/neoforge-21.1.249-client.jar']
deps += sorted((a.libraries / 'org/lwjgl').rglob('*3.3.3.jar'))
deps += sorted(a.compile_deps.glob('*.jar'))
deps.append(a.libraries / 'net/neoforged/bus/8.0.5/bus-8.0.5.jar')
assert all(x.is_file() for x in deps), 'Missing cached compilation dependencies'
args = ['--release', '21', '-proc:none', '-cp', ';'.join(map(str, deps)), '-d', str(classes)]
args += [str(x) for x in sorted((source / 'src').rglob('*.java'))]
argfile = a.output.resolve() / 'compile.args'
argfile.write_text('\n'.join('"' + x.replace('\\', '/') + '"' for x in args))
subprocess.run([str(a.jdk / 'bin/javac.exe'), '@' + str(argfile)], check=True)
import tomllib
metadata = tomllib.loads((source / 'resources/META-INF/neoforge.mods.toml').read_text())
version = metadata['mods'][0]['version']
jar = a.output / f'radiance-hybrid-foundation-{version}.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as out:
    for base in (classes, source / 'resources'):
        for path in sorted(base.rglob('*')):
            if path.is_file():
                out.writestr(path.relative_to(base).as_posix(), path.read_bytes())
report = {'jar': str(jar.resolve()), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
          'nativeHudDefault': True, 'legacyHudFallback': '-Dradiance.nativeHud=false',
          'runtimeValidation': 'pending'}
(a.output / 'manifest.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
