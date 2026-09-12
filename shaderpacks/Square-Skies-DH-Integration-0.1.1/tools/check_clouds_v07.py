"""Independent float32 traversal and flat three-sheet transport checks.

CPU mirror, not GPU execution. Uses actual seeded occupancy and nine-column
rounded density in field/cache tests; connected-sheet transport uses controlled
uniform occupancy to make any internal grid artifact unambiguously incorrect.
"""
from pathlib import Path
import numpy as np,json,hashlib,argparse
from PIL import Image,ImageDraw
w=Path(__file__).parent
args=argparse.ArgumentParser();args.add_argument('--pack',type=Path,default=w.parent if w.name=='tools' else w/'Square-Skies-Prototype-0.7');args.add_argument('--build',type=Path,default=w/'validation-v07');args=args.parse_args()
root=args.pack;out=args.build;out.mkdir(exist_ok=True)
ns={};exec((w/'check_transport_v051.py').read_text().split('rng=np.random.default_rng(511)')[0],ns)
edges=ns['new_edges'];smooth=ns['smooth'];cdf=ns['cdf'];kernel=ns['kernel'];F=np.float32
bases=np.array([192.,232.,280.]);sizes=np.array([12.,16.,22.]);coverage=np.array([.36,.32,.29]);seeds=np.array([[0,0],[941,-631],[-1277,1499]])
sun=np.array([.45,.8,.4]);sun/=np.linalg.norm(sun);rng=np.random.default_rng(707)
def hash2(cell):
 # Intentional modulo-2^32 arithmetic matches GLSL uint hashing.
 with np.errstate(over='ignore'):
  c=np.asarray(cell,dtype=np.int64);h=(c[...,0].astype(np.uint32)*np.uint32(1664525)+c[...,1].astype(np.uint32)*np.uint32(1013904223)).astype(np.uint32)
  h^=h>>np.uint32(16);h*=np.uint32(2246822519);h^=h>>np.uint32(13)
 return (h&np.uint32(0xffffff)).astype(float)/16777216.
def noise(p):
 i=np.floor(p).astype(int);f=p-i;f=f*f*(3-2*f)
 a=hash2(i)*(1-f[...,0])+hash2(i+[1,0])*f[...,0];b=hash2(i+[0,1])*(1-f[...,0])+hash2(i+[1,1])*f[...,0]
 return a*(1-f[...,1])+b*f[...,1]
def cells(cell,layer):
 q=np.asarray(cell)+seeds[layer];v=.78*noise((q+.5)/5)+.22*noise((q+17.5)/2)
 threshold=.85-.7*coverage[layer];tier=np.floor(np.clip((v-threshold)*8,0,2.999))
 return v>threshold,bases[layer]+6*(2/3+tier/6)
def mass(p,cell,layer,uniform=False):
 p=np.asarray(p);sum=np.zeros(p.shape[:-1]+(4,));sz=sizes[layer];base=bases[layer]
 for z in range(-1,2):
  for x in range(-1,2):
   c=np.asarray(cell)+[x,z];occupied,top=cells(c,layer)
   if uniform:occupied=True;top=base+6
   if not occupied:continue
   lo=np.array([c[0]*sz,base,c[1]*sz]);hi=np.array([(c[0]+1)*sz,top,(c[1]+1)*sz])
   a=p-lo;b=p-hi;v=cdf(a)-cdf(b);g=kernel(a)-kernel(b)
   sum+=np.stack([np.prod(v,axis=-1),g[...,0]*v[...,1]*v[...,2],v[...,0]*g[...,1]*v[...,2],v[...,0]*v[...,1]*g[...,2]],axis=-1)
 return sum
def spans(ro,rd,cb=256,sb=768,step=.5,sz=sizes,bs=bases,thickness=6.,radius=1.,ray_limit=2048.):
 intervals=[]
 for i in range(3):
  bottom=F(bs[i]-radius);top=F(bs[i]+thickness+radius)
  if abs(rd[1])<1e-7:
   if bottom<=ro[1]<=top and ray_limit>0:intervals.append([i,F(0),F(min(2048,ray_limit)),F(0)])
  else:
   a=F((bottom-ro[1])/rd[1]);b=F((top-ro[1])/rd[1]);start=max(min(a,b),F(0));end=min(max(a,b),F(min(2048,max(0,ray_limit))))
   if end>start:intervals.append([i,start,end,F(0)])
 intervals.sort(key=lambda s:float(s[1]));path=F(max(sb-3,1)*step)
 if not intervals:return [],F(2048),path
 limit=min(F(min(2048,max(0,ray_limit))),F(intervals[0][1]+F(max(cb-9,1)*min(sz))/max(F(abs(rd[0])+abs(rd[2])),F(.001))))
 total=F(0);kept=[]
 for i,start,end,_ in intervals:
  end=min(end,limit,F(start+max(F(path-total),F(0))))
  if end<=start:break
  kept.append([i,start,end,total]);total=F(total+F(end-start))
 return kept,limit,path
def bins(span,step=.5):
 _,a,b,_=span;n=int(np.ceil(float(F(b-a))/step));begin=F(a+np.arange(n,dtype=np.float32)*F(step));dt=np.maximum(F(0),np.minimum(F(step),F(b-begin)));center=F(begin+F(.5)*dt)
 good=(dt>0)&(center<b);return center[good],dt[good]

# Actual seeded occupancy, fixed bases and flat interior normals.
flat_error=0.;cache_error=0.;light_error=0.;gradient_error=0.;flat_cases=0
for i in range(3):
 for _ in range(200):
  c=rng.integers(-200,200,2);occ,t=cells(c,i)
  if occ:
   for rel in [np.array([.3,.3]),np.array([.7,.8])]:
    p=np.array([(c[0]+rel[0])*sizes[i],bases[i]-.1,(c[1]+rel[1])*sizes[i]])
    m=mass(p,c,i);flat_error=max(flat_error,abs(m[1]),abs(m[3]));flat_cases+=1
  # Cache identity includes all four bounded light queries at a shared edge.
  p=np.array([c[0]*sizes[i],bases[i]+rng.uniform(-1,7),(c[1]+rng.uniform(.02,.98))*sizes[i]])
  left=c-[1,0];right=c;ma=mass(p,left,i);mb=mass(p,right,i)
  cache_error=max(cache_error,float(np.max(abs(ma-mb))))
  span=min(8.,max(0.,(bases[i]+7-p[1])/sun[1]));nodes=np.array([.0694318442,.3300094782,.6699905218,.9305681558]);weights=np.array([.1739274226,.3260725774,.3260725774,.1739274226])
  q=p+sun*span*nodes[:,None];a=np.exp(-sum(weights*smooth(.06,.72,mass(q,left,i)[:,0]))*span*1.1);b=np.exp(-sum(weights*smooth(.06,.72,mass(q,right,i)[:,0]))*span*1.1)
  light_error=max(light_error,abs(a-b))
  for axis in range(3):
   delta=np.zeros(3);delta[axis]=1e-5;n=(mass(p+delta,right,i)[0]-mass(p-delta,right,i)[0])/2e-5
   gradient_error=max(gradient_error,abs(n-mb[axis+1]))
assert flat_error<1e-12 and cache_error<1e-12 and light_error<1e-12 and gradient_error<1e-7

# Float32 sorted traversal, aggregate cell/sample bounds and grazing cases.
limit_cases=0;max_cells=0;max_samples=0;order_examples={};count_three=0
directions=[(0,1,0),(0,-1,0),(1,0,0),(-1,0,0),(0,0,1),(1,1e-7,.2),(1,-1e-7,.2),(-.7,.08,.7)]
origins=[(12,y,24) for y in [66.,192.,195.,215.,232.,235.,260.,280.,283.,350.]]
random=[(rng.uniform([-2000,40,-2000],[2000,450,2000]),rng.uniform(-1,1,3)) for _ in range(600)]
for raw_ro,raw_rd in [(ro,rd) for ro in origins for rd in directions]+random:
 ro=np.array(raw_ro,np.float32);rd=np.array(raw_rd,np.float32);rd/=np.linalg.norm(rd)
 for cb,sb,step in [(256,768,.5),(32,96,.2),(512,2048,1.),(16,128,.75),(48,384,.75)]:
  ss,far,path=spans(ro,rd,cb,sb,step);limit_cases+=1;nc=0;nb=0
  if len(ss)==3:count_three+=1
  assert all(ss[j][2]<=ss[j+1][1] for j in range(len(ss)-1))
  for s in ss:
   i,a,b,prefix=s;segments=edges(ro,rd,float(a),float(b),sizes[i],0.);centers,dt=bins(s,step)
   nc+=len(segments);nb+=len(centers)
   consumed=[]
   for lo,hi in segments:consumed.extend(np.flatnonzero((centers>=lo)&(centers<hi)).tolist())
   assert consumed==list(range(len(centers))), (s,ro,rd,segments)
   assert b<=far and b<=2048 and a>=0
  assert nc<=cb and nb<=sb,(nc,cb,nb,sb,ro,rd,ss)
  max_cells=max(max_cells,nc);max_samples=max(max_samples,nb)
for y,dy in [(66,1),(350,-1),(215,1),(215,-1),(260,1),(260,-1),(195,0)]:
 rd=np.array([1,dy,0],np.float32);rd/=np.linalg.norm(rd);ss,_,_=spans(np.array([0,y,0],np.float32),rd)
 order_examples[f'y={y},dy={dy}']=[s[0] for s in ss]
assert order_examples['y=66,dy=1']==[0,1,2] and order_examples['y=350,dy=-1']==[2,1,0]
assert order_examples['y=215,dy=1']==[1,2] and order_examples['y=215,dy=-1']==[0]
assert order_examples['y=260,dy=1']==[2] and order_examples['y=260,dy=-1']==[1,0]
assert order_examples['y=195,dy=0']==[0]

# Exposed geometric extremes, the independent sky range and full occupancy:
# analytic bound must hold before safety counters can truncate any ray.
extreme_limit_cases=0
for _ in range(300):
 ro=rng.uniform([-2000,40,-2000],[2000,600,2000]).astype(np.float32);rd=rng.uniform(-1,1,3).astype(np.float32);rd/=np.linalg.norm(rd)
 sz=rng.choice([8.,24.,32.,40.],3);bs=np.array([rng.choice([160.,256.]),0.,0.]);bs[1]=bs[0]+rng.choice([24.,96.]);bs[2]=bs[1]+rng.choice([24.,128.]);thick=rng.choice([3.,12.]);rad=rng.choice([.3,2.]);ray_limit=2048.
 for cb,sb,step in [(32,96,.2),(256,768,.5),(512,2048,1.)]:
  ss,far,path=spans(ro,rd,cb,sb,step,sz,bs,thick,rad,ray_limit);nc=nb=0
  for span in ss:
   i,a,b,prefix=span;nc+=len(edges(ro,rd,float(a),float(b),sz[i],0.));nb+=len(bins(span,step)[0]);assert b<=ray_limit
  assert nc<=cb and nb<=sb,(nc,nb,cb,sb,ss)
  assert all(ss[j][2]<=ss[j+1][1] for j in range(len(ss)-1));extreme_limit_cases+=1

# Full controlled connected-sheet transport: continuous point lighting, native
# four-node local integration, fitted air and front-to-back alpha. Repartition
# bookkeeping cannot change any sample or lighting query.
def full_transport(ro,rd,partition=None):
 ss,far,path=spans(ro,rd);T=1.;C=np.zeros(3);events=[]
 for s in ss:
  i,a,b,prefix=s;center,dt=bins(s);p=ro+center[:,None]*rd
  if partition is not None:
   seg=edges(ro,rd,float(a),float(b),*partition);indices=[]
   for lo,hi in seg:indices.extend(np.flatnonzero((center>=lo)&(center<hi)).tolist())
   assert indices==list(range(len(center)))
  y=p[:,1]-bases[i];m=cdf(y)-cdf(y-6);g=kernel(y)-kernel(y-6);normal=np.zeros((len(y),3));normal[:,1]=-g/np.sqrt(g*g+.0004)
  nodes=np.array([.0694318442,.3300094782,.6699905218,.9305681558]);weights=np.array([.1739274226,.3260725774,.3260725774,.1739274226]);span=np.minimum(8.,np.maximum(0.,(7-y)/sun[1]));lighty=y[:,None]+sun[1]*span[:,None]*nodes
  vis=np.exp(-np.sum(weights*smooth(.06,.72,cdf(lighty)-cdf(lighty-6)),axis=-1)*span*1.1)
  face=.5+.5*normal[:,1];ndl=np.maximum(normal@sun,0);rim=.65*vis*(.2*(1-abs(normal@rd))**2*smooth(.0004,.0144,g*g)+.8*max(rd@sun,0)**12)
  # Distinct controlled illumination identifies incorrect ordering, while the
  # same geometry/lighting equations apply to each flat sheet.
  ambient=np.array([[.12,.19,.27],[.21,.17,.12],[.17,.21,.12]])[i]
  color=.9*(ambient*(.78+.24*face)[:,None]*(np.array([.88,.94,1])+(1-np.array([.88,.94,1]))*face[:,None])+np.array([2.4,2.3,2.1])*(.045+.075*face+vis*.5*ndl+rim)[:,None])
  airT=np.exp(-np.array([.000025,.000032,.000051])*center[:,None]);color=color*airT+np.array([.23,.39,.7])*(1-airT)
  fade=(1-smooth(min(900.,far*.75),far,center))*(1-smooth(path*.75,path,prefix+center-a));alpha=(1-np.exp(-smooth(.06,.72,m)*dt*1.1))*fade;alpha[alpha<=.00001]=0
  for t,v,c in zip(center,alpha,color):events.append((float(t),float(v),c))
 # The production traversal is already sorted; compare to an independently
 # flattened sort as a separate compositing oracle.
 def composite(es):
  T=1.;C=np.zeros(3)
  for _,alpha,color in es:
   C+=T*alpha*color;T*=1-alpha
   if T<.005:break
  return np.r_[C+T*np.array([.12,.24,.42]),T]
 return composite(events),composite(sorted(events,key=lambda x:x[0]))
partition_error=0.;order_error=0.;transport_cases=0
for _ in range(80):
 ro=np.array([rng.uniform(-600,600),rng.choice([66.,215.,260.,350.]),rng.uniform(-600,600)],np.float32);rd=rng.uniform(-1,1,3).astype(np.float32);rd/=np.linalg.norm(rd)
 ref,oracle=full_transport(ro,rd);order_error=max(order_error,float(np.max(abs(ref-oracle))))
 for part in [(8.,0.),(12.,3.25),(27.,-5.5)]:
  test,_=full_transport(ro,rd,part);partition_error=max(partition_error,float(np.max(abs(ref-test))));transport_cases+=1
assert order_error==0 and partition_error==0
# Transparent slab compositing retains all three contributions in the correct
# order rather than relying solely on opaque early-outs in the field test.
composite_error=0.
for order in [[0,1,2],[2,1,0],[1,2],[1,0]]:
 alphas=np.array([.2,.35,.4]);colors=np.eye(3);T=1.;C=np.zeros(3)
 for i in order:C+=T*alphas[i]*colors[i];T*=1-alphas[i]
 expected=sum((np.prod([1-alphas[j] for j in order[:n]])*alphas[i]*colors[i] for n,i in enumerate(order)),start=np.zeros(3))
 composite_error=max(composite_error,float(np.max(abs(C-expected))))
assert composite_error<1e-12

# No hard density jump when the sample-distance cap moves by one bin: the
# removed final bin is already in the smooth zero tail. Also cover ray limits.
fade_tail=[]
for sb in [96,128,240,768,2048]:
 path=(sb-3)*.5;fade_tail.append(float(1-smooth(path*.75,path,path-.25)))
assert max(fade_tail)<.002

coords=np.stack(np.meshgrid(np.arange(-1600,1600,8),np.arange(-1600,1600,8)),axis=-1)
masks=[]
for i in range(3):masks.append(cells(np.floor(coords/sizes[i]).astype(int),i)[0])
fractions=[float(np.mean(m)) for m in masks];corr=np.corrcoef(np.array(masks).reshape(3,-1).astype(float))
assert all(.05<f<.45 for f in fractions) and np.max(abs(corr-np.eye(3)))<.12
combined=float(np.mean(masks[0]|masks[1]|masks[2]));img=Image.new('RGB',(1200,465),(18,25,38));draw=ImageDraw.Draw(img)
draw.text((16,10),'0.7 - Three separate FLAT cloud sheets / CPU occupancy plan, not a game screenshot',fill='white')
for i,m in enumerate(masks):
 rgb=np.where(m[...,None],np.array([209,224,238]),np.array([39,68,92])).astype(np.uint8);img.paste(Image.fromarray(rgb).resize((380,380)),(10+i*400,60))
 draw.text((12+i*400,36),f'Y={int(bases[i])} | cells={int(sizes[i])} | occupied {fractions[i]:.1%}',fill='white')
draw.text((16,447),'Identical world area in each panel; independent seeded patterns. Bases never depend on X/Z.',fill=(190,205,220));img.save(out/'layer-layout.png')
code=(root/'common/square_clouds.glsl').read_text();cfg=json.loads((root/'configs.json').read_text())
assert 'ssHeightWarp' not in code and 'SS_HEIGHT_VARIATION' not in code and '16384' not in code
assert 'cellsUsed<budget&&samplesUsed<sampleBudget' in code and code.count('integrateAtmosphereSegment(rayOrigin,rd,airDistance)')==1
for name in ['square_water.glsl','square_water_volume.glsl']:
 baseline=w/'Square-Skies-Prototype-0.6/common'/name;expected=hashlib.sha256(baseline.read_bytes()).hexdigest() if baseline.exists() else json.loads((root/'validation/v051-water-sha256.json').read_text())[name]
 assert hashlib.sha256((root/'common'/name).read_bytes()).hexdigest()==expected
report=dict(success=True,scope='CPU mirror: seeded occupancy/rounded cache tests, float32 sorted traversal and bounded work, controlled connected-sheet full transport. No GLSL execution, native LUT readback or GPU timing.',flat_interior_cases=flat_cases,max_horizontal_gradient=flat_error,cache_boundary_cases=600,max_mass_gradient_cache_difference=cache_error,max_local_light_cache_difference=light_error,max_gradient_error=gradient_error,float32_limit_cases=limit_cases,three_interval_cases=count_three,max_observed_cells=max_cells,max_observed_samples=max_samples,order_examples=order_examples,full_RGBA_partition_comparisons=transport_cases,max_partition_error=partition_error,max_depth_order_error=order_error,max_transparent_layer_composition_error=composite_error,max_final_bin_fade=max(fade_tail),clear_weather_occupied_fractions=fractions,occupancy_correlations=corr.tolist(),combined_coverage=combined,water_byte_identical_to_v06=True,cloud_source_sha256=hashlib.sha256((root/'common/square_clouds.glsl').read_bytes()).hexdigest())
report['extreme_geometric_and_ray_limit_cases']=extreme_limit_cases
(out/'clouds-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
