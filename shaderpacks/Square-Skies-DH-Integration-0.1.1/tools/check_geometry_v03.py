"""Independent brute-force box intersections versus the shader's grid traversal.
Tests ordering/integration of analytic boxes, not Minecraft/Vulkan execution.
"""
import math, random, json
from pathlib import Path
SIZE=12.; BASE=192.; TOP=198.; FAR=2048.; EPS=.002
def hash2(x,z):
    h=(x*1664525+z*1013904223)&0xffffffff
    h^=h>>16;h=h*2246822519&0xffffffff;h^=h>>13
    return (h&0xffffff)/16777216
def noise(x,z):
    ix,iz=math.floor(x),math.floor(z);fx,fz=x-ix,z-iz
    fx=fx*fx*(3-2*fx);fz=fz*fz*(3-2*fz)
    a=hash2(ix,iz)*(1-fx)+hash2(ix+1,iz)*fx
    b=hash2(ix,iz+1)*(1-fx)+hash2(ix+1,iz+1)*fx
    return a*(1-fz)+b*fz
def box(x,z):
    field=.78*noise((x+.5)/5,(z+.5)/5)+.22*noise((x+17.5)/2,(z+17.5)/2)
    threshold=.85+(.15-.85)*.42
    if field<=threshold:return None
    tier=math.floor(min(max((field-threshold)*8,0),2.999))
    return (x*SIZE,BASE,z*SIZE),((x+1)*SIZE,BASE+6*(2/3+tier/6),(z+1)*SIZE)
def intersect(ro,rd,b):
    if b is None:return None
    lo,hi=b; near=-math.inf;far=math.inf
    for a in range(3):
        if abs(rd[a])<1e-7:
            if not lo[a]<=ro[a]<=hi[a]:return None
        else:
            times=sorted(((lo[a]-ro[a])/rd[a],(hi[a]-ro[a])/rd[a]))
            near=max(near,times[0]);far=min(far,times[1])
    return (near,far) if far>max(near,0) else None
def layer(ro,rd):
    if abs(rd[1])<1e-7:return (0,FAR) if BASE<=ro[1]<=TOP else None
    a,b=sorted(((BASE-ro[1])/rd[1],(TOP-ro[1])/rd[1]))
    return (max(a,0),min(b,FAR)) if min(b,FAR)>max(a,0) else None
def dda(ro,rd):
    span=layer(ro,rd)
    if span is None:return 0
    t,end=span;total=0;steps=0
    while t<end and steps<512:
        steps+=1
        cell=[math.floor((ro[a]+rd[a]*(t+EPS))/SIZE) for a in (0,2)]
        hit=intersect(ro,rd,box(*cell))
        if hit:total+=max(0,min(hit[1],end)-max(hit[0],t))
        candidates=[FAR+1]
        for k,a in enumerate((0,2)):
            if abs(rd[a])>=1e-7:
                edge=(cell[k]+(1 if rd[a]>0 else 0))*SIZE
                candidates.append((edge-ro[a])/rd[a])
        t=max(min(candidates)+EPS,t+EPS)
    assert steps<512, 'Traversal exceeded reference budget'
    return total
def brute(ro,rd):
    span=layer(ro,rd)
    if span is None:return 0
    start,end=span
    ends=[[ro[a]+rd[a]*start,ro[a]+rd[a]*end] for a in (0,2)]
    bounds=[(math.floor(min(p)/SIZE),math.floor(max(p)/SIZE)) for p in ends]
    # Collect intervals then merge: rays exactly on shared faces count once.
    intervals=[]
    for x in range(bounds[0][0],bounds[0][1]+1):
        for z in range(bounds[1][0],bounds[1][1]+1):
            hit=intersect(ro,rd,box(x,z))
            if hit:
                a,b=max(hit[0],start),min(hit[1],end)
                if b>a:intervals.append((a,b))
    total=0;last=-math.inf
    for a,b in sorted(intervals):
        total+=max(0,b-max(a,last));last=max(last,b)
    return total

rng=random.Random(4201)
rays=[((0,64,0),(0,1,0)),((0,400,0),(0,-1,0)),((0,64,0),(1,0,0)),
      ((0,195,0),(1,0,0)),((-48,195,-48),(-1,0,0)),((48,195,48),(0,0,-1)),
      ((-48,192,-48),(0,1,0)),((0,198,0),(0,-1,0))]
for _ in range(180):
    ro=(rng.uniform(-600,600),rng.choice([64,195,400]),rng.uniform(-600,600))
    d=tuple(rng.uniform(-1,1) for _ in range(3));n=math.sqrt(sum(x*x for x in d))
    rays.append((ro,tuple(x/n for x in d)))
max_error=0
for ro,rd in rays:
    a,b=dda(ro,rd),brute(ro,rd);err=abs(a-b);max_error=max(max_error,err)
    assert err<.5, f'Traversal mismatch {ro} {rd}: {a} vs {b}'
out={'rays':len(rays),'passed':True,'maximum_path_length_error_blocks':max_error,
     'tolerance_blocks':.5,'note':'Small DDA boundary epsilon removes up to 0.002 blocks per cell. Independent brute-force intervals check analytic traversal, not runtime images.'}
target=Path(__file__).parent/'validation-v03/geometry-report.json'
target.write_text(json.dumps(out,indent=2))
print(json.dumps(out,indent=2))
