"""Independent quadrature, topology, gradient and ray-integration checks.

The quadrature oracle numerically integrates the kernel on box intervals rather
than using the shader's CDF. The union reference uses every global box instead
of the shader's 3x3 neighborhood. No game/renderer is invoked.
"""
from pathlib import Path
import json,math
import numpy as np
w=Path(__file__).parent;out=w/'validation-v04';out.mkdir(exist_ok=True)
R=1.;SIZE=12.;BASE=192.
def cdf(x):
 x=np.clip(x,-1,1);return .5+.75*x-.25*x*x*x
def kernel(x):return np.where(abs(x)<1,.75*(1-x*x),0)
def interval(p,lo,hi):return cdf((p-lo)/R)-cdf((p-hi)/R)
def mass(points,boxes,gradient=False):
 points=np.asarray(points);total=np.zeros(points.shape[:-1]);g=np.zeros_like(points,dtype=float)
 for lo,hi in boxes:
  weight=interval(points,np.array(lo),np.array(hi));total+=np.prod(weight,axis=-1)
  if gradient:
   deriv=(kernel((points-lo)/R)-kernel((points-hi)/R))/R
   for j in range(3):g[...,j]+=deriv[...,j]*np.prod(weight[...,np.arange(3)!=j],axis=-1)
 return (total,g) if gradient else total
def box(x,z,h=4):return(np.array([x*SIZE,BASE,z*SIZE]),np.array([(x+1)*SIZE,BASE+h,(z+1)*SIZE]))
def quad_interval(p,lo,hi):
 # Gauss-Legendre quadrature over clipped support exactly integrates the
 # parabolic kernel, with no CDF implementation shared with the shader.
 a=max(lo,p-R);b=min(hi,p+R)
 if a>=b:return 0.
 nodes,weights=np.polynomial.legendre.leggauss(8)
 x=(a+b)*.5+(b-a)*.5*nodes
 return float(np.sum(weights*.75/R*(1-((x-p)/R)**2))*(b-a)*.5)
def oracle(p,boxes):return sum(math.prod(quad_interval(p[j],lo[j],hi[j]) for j in range(3)) for lo,hi in boxes)
rng=np.random.default_rng(401);cases=0;max_mass=0;max_gradient=0
island=[box(0,0),box(1,0),box(1,1),box(2,0,6),box(-1,-1,5)]
for p in rng.uniform([-14,190,-14],[38,200,26],size=(450,3)):
 m,g=mass(p,island,True);ref=oracle(p,island);max_mass=max(max_mass,abs(m-ref));assert abs(m-ref)<2e-12
 eps=1e-5
 for j in range(3):
  d=np.eye(3)[j]*eps;fd=(oracle(p+d,island)-oracle(p-d,island))/(2*eps)
  max_gradient=max(max_gradient,abs(g[j]-fd));assert abs(g[j]-fd)<1e-7
 cases+=1
# Exactly the same field and gradient as one joined rectangle, including
# the former internal seam and top/bottom edges. There is no bevel seam.
joined=[(np.array([0,BASE,0]),np.array([24,BASE+4,12]))]
seams=0
for x in np.linspace(10.5,13.5,31):
 for y in np.linspace(BASE-1.1,BASE+5.1,43):
  p=np.array([x,y,6.]);a,ga=mass(p,[box(0,0),box(1,0)],True);b,gb=mass(p,joined,True)
  assert abs(a-b)<1e-14 and np.max(abs(ga-gb))<1e-14;seams+=1
# Filtered cloud surface has rounded edges and corners, not only new normals.
one=[box(0,0)]
assert abs(oracle([0,194,6],one)-.5)<1e-14
assert abs(oracle([0,192,6],one)-.25)<1e-14
assert abs(oracle([0,192,0],one)-.125)<1e-14
def root(lo,hi):
 for _ in range(50):
  mid=(lo+hi)*.5
  if oracle([mid,BASE+mid,6],one)<.5:lo=mid
  else:hi=mid
 return (lo+hi)*.5
rounded_inset=root(0,1);assert .25<rounded_inset<.35
# 3x3 neighborhood equals global union for points in each DDA cell,
# including negative coordinates and isolated diagonal neighbors.
hood_cases=0
for p in rng.uniform([-15,190,-15],[40,200,30],size=(700,3)):
 cell=np.floor(p[[0,2]]/SIZE).astype(int)
 local=[(lo,hi) for lo,hi in island if np.all(abs(np.rint(lo[[0,2]]/SIZE).astype(int)-cell)<=1)]
 assert abs(mass(p,local)-oracle(p,island))<2e-12;hood_cases+=1
def density(m):
 t=np.clip((m-.06)/(.72-.06),0,1);return t*t*(3-2*t)
def integral(ro,rd,step,boxes):
 # Fixed-step global integration is independent of the shader's DDA cell
 # clipping. Compare a shader-like DDA path against this fine reference.
 ts=np.arange(step*.5,70.,step);return float(np.sum(density(mass(ro+ts[:,None]*rd,boxes)))*step)
def dda_integral(ro,rd,boxes):
 t=0.;total=0.;loops=0
 while t<70 and loops<100:
  cell=np.floor((ro+rd*(t+.0001))[[0,2]]/SIZE).astype(int);end=70.
  for k,a in enumerate((0,2)):
   if abs(rd[a])>1e-7:end=min(end,((cell[k]+(1 if rd[a]>0 else 0))*SIZE-ro[a])/rd[a])
  end=max(end,t+.00001);n=min(48,max(1,math.ceil((end-t)/.5)));dt=(end-t)/n
  ts=t+(np.arange(n)+.5)*dt
  local=[(lo,hi) for lo,hi in boxes if np.all(abs(np.rint(lo[[0,2]]/SIZE).astype(int)-cell)<=1)]
  total+=np.sum(density(mass(ro+ts[:,None]*rd,local)))*dt;t=end;loops+=1
 assert loops<100;return total
rays=0;max_path_error=0.
for ro,rd in [(np.array([-15.,194,6]),np.array([1.,0,0])),(np.array([6.,175,6]),np.array([0.,1,0])),(np.array([40.,194,6]),np.array([-1.,0,0]))]+[(np.array([-12.,190.,-12.])+rng.normal(0,4,3),np.array([1.,.1,1.])+rng.normal(0,.35,3)) for _ in range(70)]:
 rd/=np.linalg.norm(rd);fine=integral(ro,rd,.01,island);coarse=dda_integral(ro,rd,island)
 err=abs(fine-coarse);max_path_error=max(max_path_error,err);assert err<.28,(fine,coarse,err);rays+=1
report=dict(success=True,quadrature_and_gradient_points=cases,internal_seam_points=seams,neighborhood_points=hood_cases,ray_paths=rays,max_mass_error=max_mass,max_gradient_error=max_gradient,max_path_error_blocks=max_path_error,rounded_edge_diagonal_inset_blocks=rounded_inset)
(out/'rounding-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
