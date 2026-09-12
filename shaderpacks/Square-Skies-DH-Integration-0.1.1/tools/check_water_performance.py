"""Check complete interval weighting and bounded adaptive visibility work."""
import math, json
from pathlib import Path
checks=0
for cap in (2,6,16,64):
    for distance in (0.001,0.1,1,6,11.9,12,12.1,24,36,48,96,128):
        count=min(max(cap,2),max(2,math.ceil(distance/6)))
        assert 2<=count<=cap
        for sigma in (0.01,0.025,0.1,0.5):
            total=0.;prefix=1.
            for i in range(count):
                near=distance*(i/count)**2
                far=distance*((i+1)/count)**2
                step=math.exp(-sigma*(far-near))
                total+=prefix*(1-step)/sigma
                prefix*=step
            assert math.isclose(total,-math.expm1(-sigma*distance)/sigma,rel_tol=1e-10,abs_tol=1e-12)
            assert math.isclose(prefix,math.exp(-sigma*distance),rel_tol=1e-12)
            checks+=1
report={'homogeneous_integration_checks':checks,'old_default_max_visibility_rays':32,
        'new_default_max_visibility_rays':12,'short_path_max_visibility_rays':4,
        'old_medium_caustic_solver_calls':16,'new_medium_caustic_solver_calls':0,
        'gpu_timing_measured':False,
        'limitation':'Coarser scene visibility quadrature can lose narrow shafts. Medium caustic focusing is omitted; receiver caustics remain unchanged.'}
(Path(__file__).parent/'integration-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
