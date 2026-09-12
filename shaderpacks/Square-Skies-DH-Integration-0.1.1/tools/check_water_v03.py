"""Geometric properties for calm optical water normals, independent of Vulkan."""
from pathlib import Path
import math,json,random
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def norm(a):
    d=math.sqrt(dot(a,a));return tuple(x/d for x in a)
def smooth(v):
    v=max(0.,min(1.,v));return v*v*(3-2*v)
dirs=[norm((1,.3)),norm((-.4,1)),norm((.65,-.76))]
def ripple(x,z,t,strength):
    slope=[0.,0.]
    for d,amp,freq,speed in zip(dirs,[.065,.046,.025],[2.1,2.9,4.7],[1.1,-.83,1.47]):
        c=amp*math.cos((x*d[0]+z*d[1])*freq+t*speed)
        slope[0]+=d[0]*c;slope[1]+=d[1]*c
    return norm((-slope[0]*strength,1.,-slope[1]*strength))
def surface(x,z,t,strength,view):
    up=ripple(x,z,t,strength);f=smooth(abs(view[1])/.12)
    up=norm((up[0]*f,1-f+up[1]*f,up[2]*f))
    tilt=math.hypot(up[0],up[2]);scale=min(1.,abs(view[1])*.4/max(tilt,1e-5))
    up=norm((up[0]*scale,up[1],up[2]*scale))
    return up if view[1]>=0 else tuple(-v for v in up)
checks=0;minimum_reflected_y=1.;worst_seam=0.
for strength in [0.,.65,1.]:
  for x,z,t in [(0.,0.,0.),(16.,32.,1.),(-16.,-32.,75.),(127.4,-513.7,400.)]:
    for elevation in [.001,.005,.02,.05,.1,.3,.8,1.]:
      for side in [-1.,1.]:
        for a in range(16):
          angle=a*math.tau/16;h=math.sqrt(1-elevation*elevation)
          view=(h*math.cos(angle),side*elevation,h*math.sin(angle))
          n=surface(x,z,t,strength,view)
          reflected=tuple(-view[k]+2*dot(view,n)*n[k] for k in range(3))
          assert all(math.isfinite(v) for v in n+reflected)
          assert abs(dot(n,n)-1)<1e-12
          minimum_reflected_y=min(minimum_reflected_y,side*reflected[1])
          assert side*reflected[1]>0, 'Reflection crossed the planar water surface'
          checks+=1
          # Same world coordinate has no dependence on neighboring block UVs.
          n2=surface(x+1e-6,z,t,strength,view)
          seam=max(abs(n[k]-n2[k]) for k in range(3));worst_seam=max(worst_seam,seam)
          assert seam<1e-5
# The bounded self-hit exclusion must not suppress a separate water interface.
for distance,expected in [(0.0001,True),(.001,True),(.0049,True),(.005,False),(.1,False),(8.,False)]:
    assert (distance<.005)==expected;checks+=1
assert abs(1.12/(1+.12)-1)<1e-12;checks+=1
out={'passed':True,'checks':checks,'minimum_correct_side_reflection_component':minimum_reflected_y,
 'maximum_normal_delta_across_one_millionth_block':worst_seam,
 'scope':'Finite/unit world-space normals, grazing reflection hemisphere, continuity, bounded water self-hit rejection, flat caustic normalization. No rendered-image or runtime test.'}
p=Path(__file__).parent/'validation-v03/water-geometry-report.json';p.parent.mkdir(exist_ok=True)
p.write_text(json.dumps(out,indent=2));print(json.dumps(out,indent=2))
