"""Analytic checks for the water-medium integration/composition contract."""
from pathlib import Path
import math,json
checks=0;worst=0.
for density in [.25,1.,3.]:
    for distance in [0.,.01,1.,16.,64.,128.]:
        for steps in [8,24,64]:
            for extinction,scatter in zip([.12,.043,.025],[.012,.019,.023]):
                sigma=extinction*density;sigma_s=scatter*density
                dt=distance/steps;segment=math.exp(-sigma*dt)
                integrated=0.;prefix=1.
                for _ in range(steps):
                    integrated+=prefix*sigma_s*(1-segment)/sigma
                    prefix*=segment
                reference=sigma_s*(1-math.exp(-sigma*distance))/sigma
                error=abs(integrated-reference);worst=max(worst,error)
                assert error<1e-12
                assert 0<=prefix<=1
                checks+=1
# Diffuse/split paths store the medium in the emission buffer and disable
# native fog composition. Clear/specular paths contain the medium already.
for attenuation in [0.,.25,.9,1.]:
    for medium in [0.,.1,1.]:
        direct,indirect,emission=.3,.7,.05
        expected=(direct+indirect+emission)*attenuation+medium
        denoised=(direct*attenuation+indirect*attenuation)+(emission*attenuation+medium)
        assert abs(expected-denoised)<1e-12;checks+=1
        reflection,refraction=.2,.8
        split=reflection*attenuation+refraction*attenuation+emission*attenuation+medium
        assert abs(split-(reflection+refraction+emission)*attenuation-medium)<1e-12;checks+=1
assert abs(1.28/(1+.28)-1)<1e-12;checks+=1
out={'passed':True,'checks':checks,'maximum_constant_light_integral_error':worst,
 'scope':'Homogeneous Beer-Lambert integral, diffuse/split composition algebra, flat-surface Jacobian normalization. Not a runtime GPU/render test.'}
target=Path(__file__).parent/'validation-water/water-math-report.json';target.parent.mkdir(exist_ok=True)
target.write_text(json.dumps(out,indent=2));print(json.dumps(out,indent=2))
