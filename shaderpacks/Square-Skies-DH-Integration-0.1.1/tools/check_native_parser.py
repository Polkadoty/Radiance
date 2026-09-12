"""Compile actual MCVR parser bodies in an isolated, device-free harness.

Only dependency includes, private access, and parallelFor scheduling are adapted.
ZIP extraction, fallback, Vulkan pipeline creation, and expression evaluation are
outside this harness. No upstream repository files are modified.
"""
from pathlib import Path
import argparse, subprocess, json, hashlib
p=argparse.ArgumentParser()
p.add_argument('--repo',type=Path,default=Path('C:/GitHub/MCVR'))
p.add_argument('--pack',type=Path,required=True)
p.add_argument('--build',type=Path,default=Path(__file__).parent/'native-parser-validation')
a=p.parse_args();a.build.mkdir(parents=True,exist_ok=True);b=a.build.resolve()
base=a.repo/'src/core/render/modules/world/shader_pack/shader_pack'
h=base.with_suffix('.hpp').read_text();cpp=base.with_suffix('.cpp').read_text()
h=h[:h.index('struct HitShaderGroup')]
h=h.replace('#include "core/all_extern.hpp"','#include <vulkan/vulkan.h>').replace('#include "core/vulkan/all_core_vulkan.hpp"','').replace('  private:','  public:')
(b/'parser.hpp').write_text(h)
source='''#include "parser.hpp"
#include <algorithm>
#include <cctype>
#include <fstream>
#include <set>
#include <sstream>
#include <stdexcept>
#include <unordered_set>
#include <iostream>
using json = nlohmann::json;
namespace mcvr { template<class F> void parallelFor(size_t n,F f) { for(size_t i=0;i<n;++i) f(i); } }
'''
source+=cpp[cpp.index('bool ExpressionEvaluator::isValidIdentifier'):cpp.index('double ExpressionEvaluator::evaluate')]
source+=cpp[cpp.index('std::string ShaderPackLoader::toLower'):cpp.index('static uint64_t fnv1a64')]
source+=cpp[cpp.index('std::optional<std::reference_wrapper<const json>>\nShaderPackLoader::findKey'):cpp.index('ShaderPackLoader::LoadResult ShaderPackLoader::load')]
source+='''
int main(int argc,char**argv) {
 try {
  auto p=ShaderPackLoader::parseConfigFile(fs::path(argv[1]),"en_us");
  json j={{"success",true},{"attributes",p.attributes.size()},{"passes",p.passes.size()},{"sharc",p.sharc.has_value()}};
  for(const auto&a:p.attributes) if(a.name.find(".ss")!=std::string::npos) j["prototype_attributes"].push_back(a.name);
  for(const auto&pass:p.passes) if(pass.type==ShaderPackLoader::PassConfig::Type::RayTracing) j["world_definitions"]=pass.rayTracing.definitions;
  std::cout<<j.dump(2)<<std::endl;return 0;
 } catch(const std::exception&e) { std::cout<<json({{"success",false},{"error",e.what()}}).dump(2)<<std::endl;return 1; }
}
'''
(b/'parser.cpp').write_text(source)
vc=Path('C:/Program Files/Microsoft Visual Studio/2022/Preview/VC/Tools/MSVC/14.44.35112')
sdk=Path('C:/Program Files (x86)/Windows Kits/10');version='10.0.26100.0'
cmd=[str(vc/'bin/Hostx64/x64/cl.exe'),'/nologo','/std:c++20','/EHsc','/O2',str(b/'parser.cpp'),'/Fe:'+str(b/'parser.exe'),'/Fo:'+str(b/'parser.obj')]
for inc in [vc/'include',sdk/'Include'/version/'ucrt',sdk/'Include'/version/'shared',sdk/'Include'/version/'um',a.repo/'extern/json/include',Path('C:/GitHub/tools/VulkanSDK/1.4.328.1/Include')]:cmd+=['/I'+str(inc)]
cmd+=['/link','/LIBPATH:'+str(vc/'lib/x64'),'/LIBPATH:'+str(sdk/'Lib'/version/'ucrt/x64'),'/LIBPATH:'+str(sdk/'Lib'/version/'um/x64')]
c=subprocess.run(cmd,text=True,capture_output=True);(b/'compile.log').write_text(c.stdout+c.stderr)
if c.returncode:print(c.stdout+c.stderr);raise SystemExit(c.returncode)
r=subprocess.run([str(b/'parser.exe'),str(a.pack.resolve())],capture_output=True,text=True)
report=json.loads(r.stdout);report['parser_cpp_sha256']=hashlib.sha256(base.with_suffix('.cpp').read_bytes()).hexdigest();report['pack']=str(a.pack.resolve())
(b/'report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2));raise SystemExit(r.returncode)
