#include "core/render/persistent_scene.hpp"
#include <cassert>
#include <cstring>
#include <fstream>
#include <iostream>
int main(int argc,char** argv) {
    assert(argc==2);std::ifstream file(argv[1],std::ios::binary);assert(file.good());
    std::vector<char> raw((std::istreambuf_iterator<char>(file)),{});std::vector<std::byte> bytes(raw.size());std::memcpy(bytes.data(),raw.data(),raw.size());
    persistent::Scene scene;auto epoch=scene.epoch();scene.material(epoch,{1,1,17,0,.125f});scene.mesh(epoch,1,1,1,1,bytes);
    std::vector<std::byte> instances;
    auto put=[&]<class T>(T value){auto* p=reinterpret_cast<std::byte*>(&value);instances.insert(instances.end(),p,p+sizeof(T));};
    for(uint32_t v:{0x31495052u,1u,1u,0u})put(v);
    for(uint64_t v:{1ull,1ull,1ull})put(v);
    for(float v:{1.f,0.f,0.f,0.f,1.f,0.f,0.f,0.f,1.f})put(v);
    for(double v:{30000000.125,85.,-29000000.25})put(v);
    scene.instances(epoch,instances);auto frame=scene.snapshot({30000000.,85.,-29000000.});auto mesh=frame->draws[0].current.mesh;
    assert(mesh->vertices.size()==8&&mesh->indices.size()==12&&mesh->indices[6]==4);
    assert(mesh->vertices[0].color[0]==.25f&&mesh->vertices[0].uv[0]==.25f&&mesh->vertices[0].normal[2]==1);
    assert(mesh->material->texture==17&&mesh->material->emission==.125f);
    assert(frame->draws[0].current.transform.relativeTo(frame->camera)[3]==.125f);
    std::cout<<"PASS: real native Scene accepts the copied/partitioned PBR section packet and retains its material, indices, tint, UV and normal\n";
}
