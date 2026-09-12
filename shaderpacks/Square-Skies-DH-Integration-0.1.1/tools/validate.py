"""Offline structural validation and compilation; does not create Vulkan pipelines."""
from pathlib import Path
import argparse, json, re, subprocess, hashlib, time

parser=argparse.ArgumentParser()
parser.add_argument('--pack',type=Path,default=Path(__file__).parent/'Square-Skies-Prototype')
parser.add_argument('--sdk',type=Path,default=Path('C:/GitHub/tools/VulkanSDK/1.4.328.1'))
parser.add_argument('--build',type=Path,default=Path(__file__).parent/'validation')
parser.add_argument('--define',action='append',default=[],help='Override NAME=VALUE for compile checks only')
parser.add_argument('--only',action='append',default=[],help='Compile only the named relative shader source')
args=parser.parse_args()
overrides=dict(item.split('=',1) for item in args.define)
root=args.pack.resolve(); build=args.build.resolve(); build.mkdir(parents=True,exist_ok=True)
cfg=json.loads((root/'configs.json').read_text(encoding='utf-8'))
errors=[]; results=[]
def need(ok,msg):
    if not ok: errors.append(msg)
def relative(ref):
    path=(root/ref).resolve()
    need(path.is_relative_to(root) and path.is_file(), 'Missing/unsafe source: '+ref)

attrs=cfg['attributes']; passes=cfg['passes']; textures=cfg['textures']
need(len({a['name'] for a in attrs})==len(attrs),'Duplicate attribute names')
need(len({p['name'] for p in passes})==len(passes),'Duplicate pass names')
need(len({t['name'] for t in textures})==len(textures),'Duplicate texture names')
bindings=[]
for tex in textures:
    for k in ['sampled_binding','storage_binding']:
        if k in tex: bindings.append(tex[k])
    if tex['type']=='file':
        relative(tex['path'])
        if tex.get('dimension')=='3d' and tex['format']=='R8_UNORM':
            need((root/tex['path']).stat().st_size==tex['width']*tex['height']*tex['depth'],'Raw texture size mismatch')
need(len(set(bindings))==len(bindings),'Duplicate runtime texture bindings')
names={t['name'] for t in textures}
def check_commands(cmds):
    for cmd in cmds:
        if 'pass' in cmd: need(cmd['pass'] in {p['name'] for p in passes},'Unknown execution pass')
        if 'if_else' in cmd:
            check_commands(cmd['if_else'].get('then',[]));check_commands(cmd['if_else'].get('else',[]))
for key in ['execution','execution_post']:check_commands(cfg[key]['commands'])
defs={}
for a in attrs:
    v=a['default_value']; typ=a['type']
    if typ=='bool': v='1' if v in ['render_pipeline.true','true','1'] else '0'
    elif typ.startswith('enum:'): v=str(typ[5:].split('-').index(v))
    elif typ.startswith(('int_range:','float_range:')):
        bounds=re.fullmatch(r'(?:int|float)_range:(-?[0-9.]+)-(-?[0-9.]+)',typ)
        need(bool(bounds) and float(bounds[1])<=float(v)<=float(bounds[2]),'Default outside range: '+a['name'])
    definition=a.get('define',{})
    if isinstance(definition,str):defs[definition]=v
    else:
        for key,expr in definition.items():
            if isinstance(expr,str):defs[key]=re.sub(r'\bX\b',lambda _:v,expr)

def execution_source(post):
    varlist=cfg['execution_post' if post else 'execution']['global_variables']
    variables={key:val for d in varlist for key,val in d.items()}
    descriptor_set=5 if post else 6
    s=f'layout(set={descriptor_set},binding=0,std430) readonly buffer RtExecutionVariables {{\n'
    s+=''.join(f'float {key}_;\n' for key in variables)+'} rtExecutionVariables;\n'
    s+=''.join(f'#define {key} (rtExecutionVariables.{key}_ != 0.0)\n' for key in variables)
    return s

requests=[]
def shaders(obj):
    if isinstance(obj,dict):
        for k,v in obj.items():
            if k in ['rgen','rchit','rahit','rint','shader','compute','vertex','fragment'] and isinstance(v,str):yield v
            elif isinstance(v,(list,dict)):yield from shaders(v)
    elif isinstance(obj,list):
        for v in obj:yield from shaders(v)
for p in passes:
    need(sum(key in p for key in ('define','defines','definitions'))<=1,
         'Pass '+p['name']+' cannot define multiple define objects (native parser rule)')
    for direction in ['inputs','outputs']:
        for resource in p.get(direction,{}).get('images',[]):
            need(resource.startswith('out:') or resource in names,'Unknown texture: '+resource)
    if 'target' in p:need(p['target'].startswith('out:') or p['target'] in names,'Unknown target')
    if p['type']=='ray_tracing':need(p['default_hit_group'] in p['hit_groups'],'Missing default hit group')
    for src in shaders(p):
        relative(src)
        if args.only and src not in args.only:continue
        definitions=defs | p.get('defines',{}) | p.get('definitions',{})
        faces=range(6) if p.get('target')=='sky_cube' else [None]
        for face in faces:
            d=definitions.copy();d.update(overrides)
            if face is not None:d['FACE']=str(face)
            requests.append((src,d,p.get('stage')=='post_render',p['name']))
need(not (root/'extern/sharc').exists(),'SHARC source unexpectedly bundled')
if errors:raise SystemExit('\n'.join(errors))
print(f'Structural checks PASS: {len(attrs)} attributes, {len(passes)} passes, {len(textures)} textures',flush=True)
seen=set()
for src,d,post,passname in requests:
    signature=(src,tuple(sorted(d.items())),post)
    if signature in seen:continue
    seen.add(signature)
    source=(root/src).read_text(encoding='utf-8')
    first,rest=source.split('\n',1)
    generated=build/(f'{len(results):03}_'+Path(src).name)
    generated.write_text(first+'\n'+execution_source(post)+rest,encoding='utf-8')
    spv=generated.with_suffix(generated.suffix+'.spv')
    command=[str(args.sdk/'Bin/glslc.exe'),'--target-env=vulkan1.4','-O','-I'+str(root),'-I'+str((root/src).parent)]
    command+=['-D'+k+'='+v for k,v in d.items()]
    command += [str(generated),'-o',str(spv)]
    compiled=subprocess.run(command,text=True,capture_output=True)
    log=compiled.stdout+compiled.stderr
    success=compiled.returncode==0
    if success:
        valid=subprocess.run([str(args.sdk/'Bin/spirv-val.exe'),'--target-env','vulkan1.4',str(spv)],text=True,capture_output=True)
        success=valid.returncode==0;log+=valid.stdout+valid.stderr
    result={'source':src,'pass':passname,'success':success,'diagnostics':log,'sha256':hashlib.sha256((root/src).read_bytes()).hexdigest()}
    results.append(result)
    print(('PASS ' if success else 'FAIL ')+src+' ['+passname+']',flush=True)
    if not success:print(log,flush=True)
report={'pack':cfg['radiance']['display_name'],'structural_checks':'passed','scope':'Offline structural checks plus GLSL/SPIR-V validation. No native loader execution, Vulkan pipeline or Minecraft runtime test.',
 'compiler':str(args.sdk/'Bin/glslc.exe'),'target':'Vulkan 1.4','overrides':overrides,'results':results}
(build/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
raise SystemExit(0 if all(r['success'] for r in results) else 1)
