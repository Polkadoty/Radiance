"""CPU geometry illustration, not a Minecraft appearance prediction."""
from check_rounding_v04 import mass,box,BASE
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw
W,H=420,260
rd=np.array([.42,.48,.77]);rd/=np.linalg.norm(rd)
right=np.cross(rd,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,rd)
u,v=np.meshgrid(np.linspace(-24,24,W),np.linspace(15,-15,H))
ro=np.array([12.,BASE+2.,10.])+u[...,None]*right+v[...,None]*up-rd*45
boxes=[box(0,0),box(1,0),box(1,1),box(2,0,6)]
hit=np.zeros((H,W),bool);normal=np.zeros((H,W,3));near=np.zeros((H,W))
for t in np.arange(20,75,.35):
 p=ro+t*rd;m,g=mass(p,boxes,True);new=(m>=.5)&~hit
 normal[new]=-g[new];near[new]=t;hit|=new
normal/=np.maximum(np.linalg.norm(normal,axis=-1)[...,None],1e-9)
sun=np.array([-.5,.75,-.42]);sun/=np.linalg.norm(sun)
shade=np.clip(np.sum(normal*sun,axis=-1),0,1)
sky=np.zeros((H,W,3))+[.25,.39,.59]
cloud=np.array([.69,.76,.79])+shade[...,None]*np.array([.30,.23,.20])
sky[hit]=cloud[hit]
im=Image.fromarray(np.uint8(np.clip(sky,0,1)*255)).resize((840,520),Image.Resampling.LANCZOS)
canvas=Image.new('RGB',(840,575),(24,31,44));canvas.paste(im,(0,55));d=ImageDraw.Draw(canvas)
d.text((14,10),'v0.4 filtered cloud geometry - CPU diagnostic',fill='white')
d.text((14,30),'12-block joined cells; occupancy=0.5 surface. Illustration only, not game lighting.',fill=(180,192,209))
dest=Path(__file__).parent/'validation-v04/rounded-geometry.png';canvas.save(dest);print(dest.resolve())
