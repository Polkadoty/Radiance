"""Full cloud transport regression on a connected, uniformly tall cloud island.

Includes float32 old/new DDA, actual opacity/source quadrature, local-light
quadrature, old cell-averaged lighting, and RGBA comparisons under repartition.
The geometry is a large merged box interior: any cell grid visible inside it is
an integrator defect. CPU illustration uses fixed incident/sky radiance, not
the native atmosphere/DLSS pipeline.
"""
from pathlib import Path
import numpy as np,json
from PIL import Image,ImageDraw
F=np.float32;R=1.;BOTTOM=192.;TOP=198.;EXT=1.1
sky=np.array([.015,.035,.07]);ambient=np.array([.35,.40,.47]);sunlight=np.array([2.4,2.3,2.1]);sun=np.array([.45,.8,.4]);sun/=np.linalg.norm(sun)
def smooth(a,b,x):
 t=np.clip((x-a)/(b-a),0,1);return t*t*(3-2*t)
def cdf(x):x=np.clip(x,-1,1);return .5+.75*x-.25*x*x*x
def kernel(x):return np.where(abs(x)<1,.75*(1-x*x),0)
def field(y):return cdf(y-BOTTOM)-cdf(y-TOP),kernel(y-BOTTOM)-kernel(y-TOP)
def density(y):return smooth(.06,.72,field(y)[0])
def local_light(y):
 nodes=np.array([.0694318442,.3300094782,.6699905218,.9305681558]);weights=np.array([.1739274226,.3260725774,.3260725774,.1739274226])
 span=np.minimum(8.,np.maximum(0.,(TOP+1-y)/sun[1]))
 return np.exp(-np.sum(density(np.asarray(y)[...,None]+sun[1]*span[...,None]*nodes)*weights,axis=-1)*span*EXT)
def source(y,rd,old_normal=None):
 m,g=field(y)
 if old_normal is None:
  ny=-g/np.sqrt(g*g+.0004);normal=np.zeros(np.shape(y)+(3,));normal[...,1]=ny
  boundary=smooth(.0004,.0144,g*g)
 else:normal=np.broadcast_to(old_normal,np.shape(y)+(3,));boundary=1.
 face=.5+.5*normal[...,1];ndl=np.maximum(np.sum(normal*sun,axis=-1),0)
 vis=local_light(np.asarray(y));forward=max(rd@sun,0)**12
 grazing=(1-np.clip(abs(np.sum(normal*rd,axis=-1)),0,1))**2*boundary
 rim=.65*vis*(.2*grazing+.8*forward)
 cool=np.array([.88,.94,1])+(1-np.array([.88,.94,1]))*face[...,None]
 return ambient*(1.35+.3*face[...,None])*cool+sunlight*(.2+.13*face+vis*.55*ndl+rim)[...,None]
def old_edges(ro,rd,start,end,budget=256):
 t=F(start);segments=[]
 for _ in range(budget):
  if t>=end:return segments,False
  cell=np.floor((ro+rd*F(t+F(.0001)))[[0,2]]/F(12)).astype(int);nxt=F(2049)
  for k,a in enumerate((0,2)):
   if abs(rd[a])>1e-7:nxt=min(nxt,F((F((cell[k]+(1 if rd[a]>0 else 0))*12)-ro[a])/rd[a]))
  leave=min(max(nxt,F(t+F(.00001))),F(end))
  if leave<=t:return segments,True
  segments.append((float(t),float(leave)));t=leave
 return segments,t<end
def new_edges(ro,rd,start,end,size=12.,offset=0.):
 cell=np.floor((ro+rd*F(start))[[0,2]]/F(size)-F(offset/size)).astype(int)
 dirs=np.sign(rd[[0,2]]).astype(int)
 def edge(k):
  a=(0,2)[k]
  return F((F((cell[k]+(1 if rd[a]>0 else 0))*size+offset)-ro[a])/rd[a]) if abs(rd[a])>=1e-7 else F(1e20)
 nxt=np.array([edge(0),edge(1)],np.float32)
 for k in range(2):
  if nxt[k]<=start and dirs[k]!=0:cell[k]+=dirs[k];nxt[k]=edge(k)
 t=start;segments=[]
 for _ in range(4096):
  leave=min(float(min(nxt)),end)
  assert leave>=t,(leave,t)
  if leave>t:segments.append((t,leave))
  if leave>=end:return segments
  boundary=min(nxt)
  for k in range(2):
   if nxt[k]<=boundary and dirs[k]!=0:cell[k]+=dirs[k];nxt[k]=edge(k)
  assert min(nxt)>boundary # integer advance must make representable progress
  t=leave
 raise AssertionError('DDA failed to reach end')
def compose(alpha,color):
 cutoff=np.flatnonzero(np.cumprod(1-alpha)<.005)
 if len(cutoff):alpha=alpha[:cutoff[0]+1];color=color[:cutoff[0]+1]
 prefix=np.concatenate(([1.],np.cumprod(1-alpha[:-1])));T=np.prod(1-alpha)
 return np.sum(color*(prefix*alpha)[:,None],axis=0)+sky*T,T
def layer(ro,rd):
 if abs(rd[1])<1e-7:return (0.,2048.) if BOTTOM-1<=ro[1]<=TOP+1 else (0.,0.)
 a=(BOTTOM-1-ro[1])/rd[1];b=(TOP+1-ro[1])/rd[1]
 return max(min(a,b),0.),min(max(a,b),2048.)
def new_transport(ro,rd,partition=None):
 start,end=layer(ro,rd)
 if end<=start:return sky,1.,0
 bins=np.arange(int(np.ceil((end-start)/.5)));dt=np.minimum(.5,end-(start+bins*.5));centers=start+bins*.5+dt*.5
 if partition is not None:
  segments=new_edges(ro,rd,start,end,*partition);indices=[]
  for a,b in segments:indices.extend(np.flatnonzero((centers>=a)&(centers<b)).tolist())
  assert indices==list(range(len(bins))) # each global bin consumed exactly once
 y=ro[1]+rd[1]*centers;alpha=(1-np.exp(-density(y)*dt*EXT))*(1-smooth(900.,2048.,centers))
 alpha[alpha<=.00001]=0;cutoff=np.flatnonzero(np.cumprod(1-alpha)<.005);count=int(cutoff[0]+1) if len(cutoff) else len(bins)
 color,T=compose(alpha,source(y,rd));return color,T,count*5
def old_transport(ro,rd,robust=False,partition=(12.,0.)):
 start,end=layer(ro,rd)
 if end<=start:return sky,1.,False,0
 if robust:segments=new_edges(ro,rd,start,end,*partition);stall=False
 else:segments,stall=old_edges(ro,rd,start,end)
 alphas=[];colors=[];cost=0
 for a,b in segments:
  n=min(48,max(1,int(np.ceil((b-a)/.5))));dt=(b-a)/n;ts=a+(np.arange(n)+.5)*dt;y=ro[1]+rd[1]*ts
  alpha=1-np.exp(-density(y)*dt*EXT);prefix=np.concatenate(([1.],np.cumprod(1-alpha[:-1])));weights=prefix*alpha;total=1-np.prod(1-alpha);cost+=n
  if total>.0001:
   gy=np.sum(-field(y)[1]*weights);normal=np.array([0,gy,0]);length=np.linalg.norm(normal)
   normal=normal/length if length>1e-8 else -rd
   yy=np.sum(y*weights)/total
   alphas.append(total*(1-smooth(900.,2048.,a)));colors.append(source(np.array(yy),rd,normal));cost+=4
   if np.prod(1-np.array(alphas))<.005:break
 if not alphas:return sky,1.,stall,cost
 c,T=compose(np.array(alphas),np.array(colors));return c,T,stall,cost
rng=np.random.default_rng(511);stalls=0;traversals=0;max_partition=0.;old_partition=0.;cost_ratios=[]
for n in range(1000):
 ro=np.array([rng.uniform(-2000,2000),66,rng.uniform(-2000,2000)],np.float32)
 rd=np.array([rng.uniform(-1,1),rng.uniform(.065,.7),rng.uniform(-1,1)],np.float32);rd/=np.linalg.norm(rd)
 start=F(125/rd[1]);end=min(float(F(133/rd[1])),2048.)
 if start<end:
  _,stalled=old_edges(ro,rd,float(start),end);stalls+=stalled;new_edges(ro,rd,float(start),end);traversals+=1
 if n<100:
  ref,T,new_cost=new_transport(ro,rd)
  for partition in [(6.,0.),(12.,0.),(24.,0.),(12.,3.25)]:
   c,t,_=new_transport(ro,rd,partition);max_partition=max(max_partition,float(np.max(abs(c-ref))),abs(t-T))
  ca,_,_,old_cost=old_transport(ro,rd,True,(6.,0.));cb,_,_,_=old_transport(ro,rd,True,(24.,0.))
  old_partition=max(old_partition,float(np.max(abs(ca-cb))))
  if old_cost:cost_ratios.append(new_cost/old_cost)
assert stalls>100 and max_partition<1e-12 and old_partition>.01
for ro,rd in [(np.array([12,300,24],np.float32),np.array([0,-1,0],np.float32)),(np.array([-12,194,-24],np.float32),np.array([-1,0,0],np.float32)),(np.array([24,194,-12],np.float32),np.array([0,0,-1],np.float32))]:
 ref,T,_=new_transport(ro,rd)
 for partition in [(6.,0.),(12.,0.),(24.,0.),(12.,3.25)]:
  c,t,_=new_transport(ro,rd,partition);assert np.max(abs(c-ref))<1e-12 and abs(t-T)<1e-12
# Translate the camera across internal grid boundaries while the scene, light,
# and view direction remain invariant. New full RGBA must be exactly constant.
continuity=[];old_scan=[];rd=np.array([.45,.45,1.],np.float32);rd/=np.linalg.norm(rd)
for x in np.linspace(-18,18,181):
 ro=np.array([x,66,30],np.float32);c,T,_=new_transport(ro,rd,(12.,0));continuity.append(np.append(c,T));c,_,_,_=old_transport(ro,rd,True);old_scan.append(c)
span=float(np.max(np.ptp(continuity,axis=0)));assert span<1e-12
out=Path(__file__).parent/'validation-v051';out.mkdir(exist_ok=True)
# Full CPU RGBA render: continuous cloud slab, varied view directions. Coarse
# preview exposes old DDA holes and averaged-light grid; it isn't game imagery.
width,height=140,50;before=np.zeros((height,width,3));after=before.copy();holes=0
for y in range(height):
 for x in range(width):
  rd=np.array([(x/(width-1)-.5)*1.6,.11+y/(height-1)*.8,1.],np.float32);rd/=np.linalg.norm(rd)
  ro=np.array([100,66,30],np.float32)
  before[y,x],_,stalled,_=old_transport(ro,rd);after[y,x],_,_=new_transport(ro,rd);holes+=stalled
def display(a):return Image.fromarray(np.uint8(np.clip(a/(1+a),0,1)**(1/2.2)*255)).resize((700,250),Image.Resampling.NEAREST)
im=Image.new('RGB',(700,560),(22,28,39));im.paste(display(before),(0,30));im.paste(display(after),(0,310));draw=ImageDraw.Draw(im)
draw.text((10,10),'Old float32 traversal + cell-averaged lighting (CPU transport)',fill='white')
draw.text((10,290),'0.5.1 integer traversal + global bins + point lighting (CPU transport)',fill='white')
im.save(out/'transport-comparison.png')
report=dict(success=True,float32_rays=traversals,old_stalled_rays=stalls,new_stalled_rays=0,full_transport_partition_cases=400,max_new_rgba_partition_error=max_partition,max_old_rgb_partition_error=old_partition,boundary_scan_rays=181,new_boundary_rgba_span=span,old_boundary_max_rgb_step=float(np.max(abs(np.diff(old_scan,axis=0)))),cpu_image_rays=width*height,cpu_old_stalled_pixels=holes,median_density_kernel_cost_ratio=float(np.median(cost_ratios)),cost_note='Density-kernel evaluations only. New lighting reuses 3x3 occupancy; old shadow-DDA occupancy/noise reload cost not counted. GPU timing required.')
(out/'transport-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
