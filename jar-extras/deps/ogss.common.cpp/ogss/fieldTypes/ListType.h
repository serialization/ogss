//
// Created by Timm Felden on 01.04.19.
//

#ifndef OGSS_TEST_CPP_LIST_TYPE_H
#define OGSS_TEST_CPP_LIST_TYPE_H

#include "../api/Arrays.h"
#include "SingleArgumentType.h"

namespace ogss {
namespace fieldTypes {

/**
 * An abstract list type. Operations that require knowledge of the actual type
 * are split to the Implementation part. The type parameter is the same as for
 * the managed Array class.
 *
 * @todo implemented tyr.containers.ALL in C++ and use it instead of Array
 */
template <typename T> class ListType final : public SingleArgumentType {

    BlockID allocateInstances(int count, streams::MappedInStream *in) final {

        // check for blocks
        if (count > HD_Threshold) {
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
            idMap.push_back(new api::Array<T>());
        return 0;
    }

    void read(ObjectID i, const ObjectID end,
              streams::MappedInStream *in) final {
        while (i < end) {
            auto xs = (api::Array<T> *)idMap[++i];
            int s = in->v32();
            xs->reserve(s);
            while (s-- != 0) {
                xs->push_back(api::unbox<T>(base->r(*in)));
            }
        }
    }

    void write(ObjectID i, const ObjectID end,
               streams::BufferedOutStream *out) const final {
        while (i < end) {
            auto xs = (api::Array<T> *)idMap[++i];
            out->v64((int)xs->size());
            for (T x : *xs) {
                base->w(api::box(x), out);
            }
        }
    }

    void writeDecl(streams::BufferedOutStream &out) const final {
        out.i8(1);
        out.v64(base->typeID);
    }

  public:
    ListType(TypeID tid, uint32_t kcc, FieldType *const base) :
      SingleArgumentType(tid, kcc, base) {}

    ~ListType() final {
        for (void *v : idMap)
            delete (api::Array<T> *)v;
    }

    /// simplify code generation
    inline api::Array<T> *read(streams::InStream &in) {
        return (api::Array<T> *)r(in).list;
    }
};
} // namespace fieldTypes
} // namespace ogss

#endif // OGSS_TEST_CPP_LIST_TYPE_H
