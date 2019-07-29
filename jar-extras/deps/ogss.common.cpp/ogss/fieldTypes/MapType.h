//
// Created by Timm Felden on 01.04.19.
//

#ifndef OGSS_TEST_CPP_MAPTYPE_H
#define OGSS_TEST_CPP_MAPTYPE_H

#include "../api/Maps.h"
#include "ContainerType.h"
#include "FieldType.h"

namespace ogss {
namespace fieldTypes {
template <typename K, typename V> class MapType final : public ContainerType {
  public:
    FieldType *const keyType;
    FieldType *const valueType;

  protected:
    BlockID allocateInstances(int count, streams::MappedInStream *in) final {

        // check for blocks
        if (count >= HD_Threshold) {
            const BlockID block = in->v32();

            // note: in contrast to Java, there is no easy way to do
            // synchronized(this) Also, allocation is faster compared to Java
            // Lastly, a container block cannot be dropped if size is nonzero
            // Hence, we will allocate all instances in block 0

            if (block)
                return block;
            // else, behave as if there were no blocks
        }
        // else, no blocks
        idMap.reserve(count);
        while (count-- != 0)
            idMap.push_back(new api::Map<K, V>());
        return 0;
    }

    void read(ObjectID i, const ObjectID end,
              streams::MappedInStream *in) final {
        while (i < end) {
            auto xs = (api::Map<K, V> *)idMap[++i];
            int s = in->v32();
            xs->reserve(s);
            while (s-- != 0) {
                (*xs)[api::unbox<K>(keyType->r(*in))] =
                  api::unbox<V>(valueType->r(*in));
            }
        }
    }

    void write(ObjectID i, const ObjectID end,
               streams::BufferedOutStream *out) const final {
        while (i < end) {
            auto xs = (api::Map<K, V> *)idMap[++i];
            out->v64((int)xs->size());
            for (std::pair<K, V> x : *xs) {
                keyType->w(api::box(x.first), out);
                valueType->w(api::box(x.second), out);
            }
        }
    }

    void writeDecl(streams::BufferedOutStream &out) const final {
        out.i8(3);
        out.v64(keyType->typeID);
        out.v64(valueType->typeID);
    }

  public:
    MapType(TypeID tid, uint32_t kcc, FieldType *const keyType,
            FieldType *const valueType) :
      ContainerType(tid, kcc),
      keyType(keyType),
      valueType(valueType){};

    ~MapType() final {
        for (void *v : idMap)
            delete (api::Map<K, V> *)v;
    }

    /// simplify code generation
    inline api::Map<K, V> *read(streams::InStream &in) {
        return (api::Map<K, V> *)r(in).map;
    }
};
} // namespace fieldTypes
} // namespace ogss

#endif // OGSS_TEST_CPP_MAPTYPE_H
