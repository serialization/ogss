//
// Created by Timm Felden on 28.01.16.
//

#ifndef OGSS_COMMON_DISTRIBUTEDFIELD_H
#define OGSS_COMMON_DISTRIBUTEDFIELD_H

#include "DataField.h"
#include <unordered_map>

namespace ogss {
namespace internal {

class DistributedField : public DataField {
  protected:
    //! data is shifted by owner.bpo + 1
    //! @note valid iff data != null (lazy field will reuse this before
    //! allocation of data)
    mutable ObjectID firstID;
    //! data holds pointers in [firstID; lastID[
    //! @note valid iff data != null (lazy field will reuse this before
    //! allocation of data)
    mutable ObjectID lastID;
    /**
     * field data corresponding to Pool::data
     * @note the array contains data for 0 -> (lastID-firstID), i.e. access has
     * to be shifted by firstID
     */
    mutable api::Box *data;
    mutable std::unordered_map<const api::Object *, api::Box> newData;

  public:
    DistributedField(const FieldType *const type, api::String name,
                     const TypeID index, AbstractPool *const owner) :
      DataField(type, name, index, owner),
      firstID(owner->bpo + 1),
      lastID(firstID + owner->cachedSize),
      data((api::Box *)calloc(lastID - firstID, sizeof(api::Box))),
      newData() {}

    ~DistributedField() override;

    api::Box getR(const api::Object *i) override;

    void setR(api::Object *i, api::Box v) override;

    void read(int i, int last, streams::MappedInStream &in) const override;

    void compress(ObjectID newLBPO) const;

    bool write(int i, int last, streams::BufferedOutStream *out) const final;

    bool check() const override;
};
} // namespace internal
} // namespace ogss

#endif // OGSS_COMMON_DISTRIBUTEDFIELD_H
