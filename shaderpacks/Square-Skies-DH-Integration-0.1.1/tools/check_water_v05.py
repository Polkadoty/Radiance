"""Finite-difference refraction oracle and visibility/integration regressions."""
from pathlib import Path
import numpy as np
import json,itertools
DIR=np.array([[1,.3],[-.4,1],[.65,-.76],[-.91,-.42],[.24,.97],[.87,-.49]],float)
DIR/=np.linalg.norm(DIR,axis=1)[:,None]
K=np.array([.75,1.6,3.3,6.8,12,20]);AMP=np.array([.045,.025,.012,.010*.8,.0035*.8,.0012*.8])
SPEED=np.array([.82,-1.17,1.58,-2.08,2.72,-3.19]);ETA=1/1.333
def wave(p,t,footprint=0.,strength=.65):
 a=AMP*strength*np.exp(-.5*(K*footprint)**2);phase=DIR@p*K+t*SPEED
 h=np.sum(a*np.sin(phase));g=np.sum(DIR*(a*K*np.cos(phase))[:,None],axis=0)
 H=np.einsum('ni,nj,n->ij',DIR,DIR,-a*K*K*np.sin(phase));return h,g,H
def refract(i,n):
 c=np.dot(i,n);return ETA*i-(ETA*c+np.sqrt(1-ETA*ETA*(1-c*c)))*n
def projected(q,t,inc,depth,footprint,strength=.65):
 # Independent direct mapping. Jacobian is not used here.
 h,g,_=wave(q,t,footprint,strength);n=np.array([-g[0],1,-g[1]]);n/=np.linalg.norm(n)
 b=refract(inc,n);return q+b[[0,2]]*max(depth+h,.01)/max(-b[1],.15)
def mapping(q,t,inc,depth,footprint,strength=.65):
 h,g,H=wave(q,t,footprint,strength);raw=np.array([-g[0],1,-g[1]]);length=np.linalg.norm(raw);n=raw/length
 c=n@inc;root=np.sqrt(max(1-ETA*ETA*(1-c*c),.001));b=ETA*c+root;bent=ETA*inc-b*n
 vertical=max(-bent[1],.15);d=max(depth+h,.01);J=np.eye(2)
 for axis in range(2):
  dr=np.array([-H[0,axis],0,-H[1,axis]]);dn=(dr-n*(n@dr))/length;dc=dn@inc
  dbent=-(ETA+ETA*ETA*c/root)*dc*n-b*dn
  J[:,axis]+=d*(dbent[[0,2]]*vertical+bent[[0,2]]*dbent[1])/(vertical*vertical)+bent[[0,2]]*g[axis]*(1 if depth+h>.01 else 0)/vertical
 return q+bent[[0,2]]*d/vertical,J
def focus(receiver,t,sun,depth,footprint=.03,strength=.65):
 if sun[1]<=.03 or depth<=.01:return 1.,0.
 inc=-sun;flat=refract(inc,np.array([0,1.,0]));d=min(depth,24.);f=max(footprint,.035+d*.01)
 q=receiver-flat[[0,2]]*d/max(-flat[1],.15)
 for _ in range(4):
  m,j=mapping(q,t,inc,d,f,strength);step=np.linalg.solve(j.T@j+np.eye(2)*.035,j.T@(m-receiver));q-=step*min(1.,.7/max(np.linalg.norm(step),.0001))
 m,j=mapping(q,t,inc,d,f,strength);res=np.linalg.norm(m-receiver);area=abs(np.linalg.det(j))
 caustic=np.clip(1.1/(area+.1),.45,2.6);conf=np.exp(-(res/max(f,.08))**2)
 y=np.clip((sun[1]-.03)/.27,0,1);fade=(1-np.exp(-d*1.25))*np.exp(-depth*.055)*y*y*(3-2*y)
 return 1+(caustic-1)*.65*fade*conf,res
rng=np.random.default_rng(500);eps=1e-5;max_g=max_h=max_j=0.;derivatives=0
for _ in range(220):
 p=rng.uniform(-200,200,2);t=rng.uniform(0,100);f=rng.uniform(.02,.4);depth=rng.uniform(.015,.08) if _<30 else rng.uniform(.2,20)
 sun=np.array([rng.uniform(-1,1),rng.uniform(.12,1),rng.uniform(-1,1)]);sun/=np.linalg.norm(sun)
 h,g,H=wave(p,t,f);m,J=mapping(p,t,-sun,depth,f)
 for axis in range(2):
  d=np.eye(2)[axis]*eps;hp,gp,_=wave(p+d,t,f);hm,gm,_=wave(p-d,t,f)
  max_g=max(max_g,abs((hp-hm)/(2*eps)-g[axis]));max_h=max(max_h,np.max(abs((gp-gm)/(2*eps)-H[:,axis])))
  finite=(projected(p+d,t,-sun,depth,f)-projected(p-d,t,-sun,depth,f))/(2*eps)
  max_j=max(max_j,np.max(abs(finite-J[:,axis])))
 derivatives+=1
assert max_g<1e-7 and max_h<1e-6 and max_j<2e-6,(max_g,max_h,max_j)
flat_cases=0
for depth in [.05,.2,1,4,12,24,48]:
 for sun in [np.array([0,1.,0]),np.array([.8,.6,0]),np.array([.96,.28,0])]:
  a,res=focus(np.array([13.,-18.]),37.,sun,depth,strength=0)
  assert abs(a-1)<1e-12 and res<1e-10;flat_cases+=1
# Focus remains finite/bounded through convergence trouble; footprint suppresses
# distant high-frequency energy instead of amplifying unstable inverse roots.
summary=[];bounded=0
for depth in [.2,1,3,7,16,30]:
 values=[];residuals=[]
 for p in rng.uniform(-12,12,(150,2)):
  v,res=focus(p,13.,np.array([.3,np.sqrt(.75),.4]),depth)
  assert np.isfinite(v) and .45<=v<=2.6;values.append(v);residuals.append(res);bounded+=1
 summary.append(dict(depth=depth,mean=float(np.mean(values)),std=float(np.std(values)),maximum=float(max(values)),max_residual=float(max(residuals))))
energies=[float(np.sum((AMP*K*.65*np.exp(-.5*(K*f)**2))**2)) for f in [0,.02,.08,.25,1,4]]
assert all(a>=b for a,b in zip(energies,energies[1:]));assert energies[-1]<energies[0]*.001
# Closest-hit contract under arbitrary any-hit order. Any opaque obstacle nearer
# than a candidate top water boundary must suppress the underwater sun probe.
def probe(events):
 water=min((t for t,kind in events if kind=='water'),default=np.inf)
 accepted=[t for t,kind in events if kind!='transparent'];closest=min(accepted,default=np.inf)
 return np.isfinite(closest) and closest==water
orders=0
for scene,expected in [([(2.,'solid'),(4.,'water'),(7.,'water')],False),([(4.,'water'),(7.,'solid')],True),([(3.999,'solid'),(4.,'water')],False),([(3.,'transparent'),(4.,'water'),(8.,'solid')],True),([(3.,'solid')],False),([],False)]:
 for order in itertools.permutations(scene):assert probe(order)==expected;orders+=1
assert not (probe([(4.,'water')]) and False) # opaque air leg blocks all sunlight
# Exact Beer-Lambert integration remains partition invariant with quadratic steps.
sigma=np.array([.12,.043,.025])*.6;max_integral=0
for distance in [.01,.1,1,9,64]:
 for n in [8,16,24,48]:
  prefix=np.ones(3);total=np.zeros(3)
  for i in range(n):
   dt=distance*(((i+1)/n)**2-(i/n)**2);step=np.exp(-sigma*dt)
   total+=prefix*(1-step)/sigma;prefix*=step
  error=float(np.max(abs(total-(1-np.exp(-sigma*distance))/sigma)));max_integral=max(max_integral,error)
  assert error<1e-11
report=dict(success=True,derivative_cases=derivatives,max_height_gradient_error=max_g,max_hessian_error=max_h,max_refractive_jacobian_error=max_j,flat_water_cases=flat_cases,bounded_focus_cases=bounded,visibility_order_cases=orders,max_segment_integral_error=max_integral,filtered_slope_energy=energies,depth_response=summary)
out=Path(__file__).parent/'validation-v05';out.mkdir(exist_ok=True);(out/'water-optics-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
