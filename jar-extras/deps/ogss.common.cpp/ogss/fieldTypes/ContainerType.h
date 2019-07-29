//
// Created by Timm Felden on 24.07.19.
//

#ifndef OGSS_COMMON_CONTAINERTYPE_H
#define OGSS_COMMON_CONTAINERTYPE_H

#include "HullType.h"

namespace ogss {
namespace fieldTypes {
class ContainerType : public HullType {
  protected:
    ContainerType(TypeID tid, uint32_t kcc) : HullType(tid, kcc), blocks(0) {}

    //! number of remaining blocks while treating this field
    //! @note this field is initialized on use and has no meaning otherwise
    mutable std::atomic<int32_t> blocks;

    /**
     * Read the hull data from the stream. Abstract, because the inner loop is
     * type-dependent anyway.
     *
     * @note the fieldID is written by the caller
     * @return true iff hull shall be discarded (i.e. it is empty)
     */
    virtual void read(ObjectID i, ObjectID end,
                      streams::MappedInStream *in) = 0;

    /**
     * Write the hull into the stream. Abstract, because the inner loop is
     * type-dependent anyway.
     *
     * @note the fieldID is written by the caller
     * @return true iff hull shall be discarded (i.e. it is empty)
     */
    virtual void write(ObjectID i, ObjectID end,
                       streams::BufferedOutStream *out) const = 0;

    api::Box get(ObjectID ID) const final {
        return api::box(((0 < ID) & (ID < (ObjectID)idMap.size())) ? idMap[ID]
                                                                   : nullptr);
    }

    friend class internal::Creator;

    friend class internal::Parser;

    friend class internal::ParParser;

    friend class internal::PHRT;

    friend class internal::SeqParser;

    friend class internal::SHRT;

    friend struct internal::StateInitializer;

    friend class internal::Writer;
};
} // namespace fieldTypes
} // namespace ogss

#endif // OGSS_COMMON_CONTAINERTYPE_H
