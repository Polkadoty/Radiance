"""CPU mirror of v0.6 cloud density, point lighting and aerial compositing.

Tests a connected island with a continuously warped underside, not merely
isolated density values. Atmosphere tests use specified coefficients/source;
no native LUT readback, Vulkan execution or tone mapping is implied.
"""
from pathlib import Path
import numpy as np,json,hashlib,argparse
from PIL import Image,ImageDraw
w=Path(__file__).parent
args=argparse.ArgumentParser();args.add_argument('--pack',type=Path,default=w.parent if w.name=='tools' else w/'Square-Skies-Prototype-0.6');args.add_argument('--build',type=Path,default=w/'validation-v06');args=args.parse_args()
root=args.pack;out=args.build;out.mkdir(exist_ok=True)
# Use the tested float32 DDA implementation, without executing the old report.
ns={};exec((w/'check_transport_v051.py').read_text().split('rng=np.random.default_rng(511)')[0],ns)
new_edges=ns['new_edges'];smooth=ns['smooth'];cdf=ns['cdf'];kernel=ns['kernel']
rng=np.random.default_rng(606);R=1.;EXT=1.1
sun=np.array([.45,.8,.4]);sun/=np.linalg.norm(sun)
ambient=np.array([.35,.4,.47]);sunlight=np.array([2.4,2.3,2.1]);sky=np.array([.12,.24,.42])
sigma=np.array([.000005802,.000013558,.0000331])*np.exp(-160/8000)+.000021*np.exp(-160/1200)
air_source=np.array([.23,.39,.7])
def warp(p,amp=18.,scale=256.):
 k=2*np.pi/max(scale,96.);d0=np.array([.8,.6]);d1=np.array([-.44721360,.89442719]);xz=np.asarray(p)[...,[0,2]]
 a=xz@d0*k+.7;b=xz@d1*(k*.61)-1.1
 return amp*(.65*np.sin(a)+.35*np.sin(b)),amp*k*(.65*np.cos(a)[...,None]*d0+.35*.61*np.cos(b)[...,None]*d1)
def field(p,amp=18.,scale=256.):
 h,g=warp(p,amp,scale);y=p[...,1]-h
 m=cdf(y-192)-cdf(y-198);dy=kernel(y-192)-kernel(y-198)
 grad=np.stack([-dy*g[...,0],dy,-dy*g[...,1]],axis=-1)
 return m,grad
def rho(p):return smooth(.06,.72,field(p)[0])
def local_light(p):
 nodes=np.array([.0694318442,.3300094782,.6699905218,.9305681558]);weights=np.array([.1739274226,.3260725774,.3260725774,.1739274226])
 span=np.minimum(8.,np.maximum(0.,(198+1+18-p[...,1])/sun[1]))
 q=p[...,None,:]+sun*span[...,None,None]*nodes[:,None]
 return np.exp(-np.sum(rho(q)*weights,axis=-1)*span*EXT)
def source(p,rd,night=False):
 m,g=field(p);g2=np.sum(g*g,axis=-1);normal=-g/np.sqrt(g2+.0004)[...,None]
 face=.5+.5*normal[...,1];ndl=np.maximum(normal@sun,0);vis=local_light(p)
 forward=max(rd@sun,0)**12;boundary=smooth(.0004,.0144,g2)
 grazing=(1-np.clip(abs(normal@rd),0,1))**2*boundary
 rim=.65*vis*(.2*grazing+.8*forward);cool=np.array([.88,.94,1])+(1-np.array([.88,.94,1]))*face[...,None]
 a=ambient*.35 if night else ambient
 light=sunlight*np.array([.08,.1,.2])*.18 if night else sunlight
 return .9*(a*(.78+.24*face)[...,None]*cool+light*(.045+.075*face+vis*.5*ndl+rim)[...,None])
def transport(ro,rd,partition=(12.,0.),night=False):
 if abs(rd[1])<1e-7:
  if not 173<=ro[1]<=217:return sky,1.,(0,0)
  start,end=0.,2048.
 else:
  a=(173-ro[1])/rd[1];b=(217-ro[1])/rd[1];start=max(min(a,b),0.);end=min(max(a,b),2048.)
 if end<=start:return sky,1.,(0,0)
 # Same conservative traversal-distance budget as the shader, independent
 # of the alternative bookkeeping partitions used by this regression.
 limit=min(2048.,start+253*12/max(abs(rd[0])+abs(rd[2]),.001));end=min(end,limit)
 bins=np.arange(int(np.ceil((end-start)/.5)));dt=np.minimum(.5,end-(start+bins*.5));centers=start+bins*.5+.5*dt
 segments=new_edges(ro,rd,start,end,*partition);indices=[]
 for a,b in segments:indices.extend(np.flatnonzero((centers>=a)&(centers<b)).tolist())
 assert indices==list(range(len(bins))), 'Every global bin must be consumed exactly once'
 p=ro+rd*centers[:,None];alpha=(1-np.exp(-rho(p)*dt*EXT))*(1-smooth(min(900.,limit*.75),limit,centers));alpha[alpha<=.00001]=0
 cutoff=np.flatnonzero(np.cumprod(1-alpha)<.005);n=int(cutoff[0]+1) if len(cutoff) else len(alpha)
 p=p[:n];alpha=alpha[:n];centers=centers[:n];active=alpha>0
 color=np.zeros((n,3));color[active]=source(p[active],rd,night)
 airT=np.exp(-sigma*centers[:,None]);color=color*airT+air_source*(1-airT)
 prefix=np.concatenate(([1.],np.cumprod(1-alpha[:-1])));T=np.prod(1-alpha)
 return (np.sum(color*(prefix*alpha)[:,None],axis=0)+sky*T),T,(n,int(np.count_nonzero(active))*4)

# Analytic chain-rule derivatives and conservative bounds, including extremes.
grad_error=0.;height_grad_error=0.
for amp,scale in [(0.,256.),(18.,256.),(36.,96.),(36.,768.)]:
 p=rng.uniform(-2500,2500,(600,3));h,_=warp(p,amp,scale);p[:,1]=h+rng.uniform(191.01,198.99,600)
 m,g=field(p,amp,scale)
 for axis in range(3):
  d=np.zeros(3);d[axis]=1e-5
  numeric=(field(p+d,amp,scale)[0]-field(p-d,amp,scale)[0])/2e-5
  grad_error=max(grad_error,float(np.max(abs(g[:,axis]-numeric))))
 assert np.max(abs(h))<=amp+1e-10
 assert np.all((p[:,1]>=192-1-amp)&(p[:,1]<=198+1+amp))
assert grad_error<1e-7

# A true nine-cell cache test with mixed stepped tops. Compare either cache
# at a shared cell edge, including all four light queries. Warp is applied to
# query coordinates, so it cannot shift or tear the XZ occupancy partition.
def top(x,z):return 192. if (x*13+z*7)%7==0 else 196.+((x*11+z*3)%3)
def cached(p,cell):
 h,g=warp(p);q=p.copy();q[1]-=h;v=np.zeros(4)
 for z in range(cell[1]-1,cell[1]+2):
  for x in range(cell[0]-1,cell[0]+2):
   t=top(x,z)
   if t<=192:continue
   lo=np.array([x*12,192,z*12]);hi=np.array([(x+1)*12,t,(z+1)*12]);a=q-lo;b=q-hi
   u=cdf(a)-cdf(b);d=kernel(a)-kernel(b)
   v+=np.array([np.prod(u),d[0]*u[1]*u[2],u[0]*d[1]*u[2],u[0]*u[1]*d[2]])
 v[1]-=v[2]*g[0];v[3]-=v[2]*g[1];return v
cache_error=0.;light_cache_error=0.
nodes=np.array([.0694318442,.3300094782,.6699905218,.9305681558]);weights=np.array([.1739274226,.3260725774,.3260725774,.1739274226])
for i in range(250):
 c=rng.integers(-20,20,2);p=np.array([c[0]*12.,0.,(c[1]+rng.uniform(.02,.98))*12]);p[1]=warp(p)[0]+rng.uniform(191,199)
 left=c-np.array([1,0]);right=c
 cache_error=max(cache_error,float(np.max(abs(cached(p,left)-cached(p,right)))))
 span=min(8.,max(0.,(217-p[1])/sun[1]));q=p+sun*span*nodes[:,None]
 ta=[]
 for cell in [left,right]:ta.append(np.exp(-sum(wt*smooth(.06,.72,cached(pt,cell)[0]) for wt,pt in zip(weights,q))*span*EXT))
 light_cache_error=max(light_cache_error,abs(ta[0]-ta[1]))
assert cache_error<1e-12 and light_cache_error<1e-12

partition_error=0.;cases=0;view_count=[];light_count=[]
for i in range(80):
 ro=np.array([rng.uniform(-1800,1800),rng.choice([66.,195.,250.]),rng.uniform(-1800,1800)],np.float32)
 rd=np.array([rng.uniform(-1,1),rng.uniform(-.4,.4),rng.uniform(-1,1)],np.float32);rd/=np.linalg.norm(rd)
 c,t,cost=transport(ro,rd);view_count.append(cost[0]);light_count.append(cost[1])
 for part in [(6.,0.),(24.,0.),(12.,3.25),(12.,-5.5)]:
  a,b,_=transport(ro,rd,part);partition_error=max(partition_error,float(np.max(abs(a-c))),abs(b-t));cases+=1
assert partition_error<1e-12

# Near an arbitrary grid boundary only the smooth geometry/light can change.
# Bound the actual integrated RGB jump across a 0.0002-block camera displacement.
boundary_jump=0.
for x in range(-120,121,12):
 ro=np.array([float(x),66.,31.],np.float32);rd=np.array([.5,.35,.6],np.float32);rd/=np.linalg.norm(rd)
 d=np.array([.0001,0,0],np.float32);a,_,_=transport(ro-d,rd);b,_,_=transport(ro+d,rd)
 boundary_jump=max(boundary_jump,float(np.max(abs(a-b))))
assert boundary_jump<.002

# Atmospheric transform: no optical-depth => identity; exact homogeneous
# semigroup; opacity compositing includes foreground air once, not twice.
air_error=0.
for _ in range(500):
 c=rng.uniform(0,5,3);sig=rng.uniform(0,.001,3);src=rng.uniform(0,1,3);a,b=rng.uniform(0,2048,2)
 def air(c,t):T=np.exp(-sig*t);return c*T+src*(1-T)
 air_error=max(air_error,float(np.max(abs(air(air(c,a),b)-air(c,a+b)))))
 assert np.all(np.exp(-sig*(a+b))<=np.exp(-sig*a))
assert air_error<1e-12
# Quantify the effective homogeneous-air fit over camera-to-cloud paths using
# the scene's actual exponential scale heights/coefficient defaults. Incident
# light is fixed here; the production endpoint uses native LUT samples.
fit_error=0.
for height,dy in [(66.,.08),(66.,.2),(66.,.8),(300.,-.4),(1000.,-.8)]:
 end=min(2048.,abs((217-height)/dy))
 def optical(t):
  r=np.exp(-height/8000)*8000/dy*(1-np.exp(-dy*t/8000))
  m=np.exp(-height/1200)*1200/dy*(1-np.exp(-dy*t/1200))
  return np.array([.000005802,.000013558,.0000331])*r+.000021*m
 endpoint=optical(end)
 for fraction in np.linspace(0,1,101):
  fit_error=max(fit_error,float(np.max(abs(np.exp(-endpoint*fraction)-np.exp(-optical(end*fraction))))))
assert fit_error<.002
# Matched incident ambient establishes cloud-only response change; real night
# sky irradiance and display exposure must still be evaluated in Minecraft.
pts=np.array([[0.,0.,0.],[25.,0.,31.],[70.,0.,-10.]])
pts[:,1]=warp(pts)[0]+np.array([191.8,198.2,195.]);rd=np.array([0.,1.,0.])
ratio=source(pts,rd,True)/source(pts,rd,False)
assert np.max(ratio)<.35

code=(root/'common/square_clouds.glsl').read_text()
for token in ['sum.y-=sum.z*warp.y','sum.w-=sum.z*warp.z','integrateAtmosphereSegment(rayOrigin,rd,endT)','color=color*airT+airSource','ssAdvanceDda(dda,ro,rd)']:assert token in code
for name in ['square_water.glsl','square_water_volume.glsl']:
 baseline=w/'Square-Skies-Prototype-0.5.1/common'/name
 expected=hashlib.sha256(baseline.read_bytes()).hexdigest() if baseline.exists() else json.loads((root/'validation/v051-water-sha256.json').read_text())[name]
 assert hashlib.sha256((root/'common'/name).read_bytes()).hexdigest()==expected
report=dict(success=True,scope='CPU mirror, connected warped island and mixed-height occupancy caches. Fixed incident radiance; no native atmosphere LUT readback or GPU timing.',height_gradient_cases=2400,max_gradient_error=grad_error,cache_boundary_cases=250,max_density_gradient_cache_error=cache_error,max_local_light_cache_error=light_cache_error,full_RGBA_partition_comparisons=cases,max_partition_error=partition_error,max_near_boundary_RGB_jump=boundary_jump,atmosphere_semigroup_cases=500,max_atmosphere_error=air_error,atmosphere_vertical_fit_points=505,max_atmosphere_transmittance_fit_error=fit_error,matched_incident_cloud_night_day_rgb_ratio=ratio.tolist(),view_bins_mean=float(np.mean(view_count)),local_light_queries_mean=float(np.mean(light_count)),atmosphere_LUT_queries_per_cloud_ray=8,cloud_source_sha256=hashlib.sha256(code.encode()).hexdigest(),water_byte_identical_to_v051=True)
report['cloud_source_sha256']=hashlib.sha256((root/'common/square_clouds.glsl').read_bytes()).hexdigest()
(out/'clouds-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
