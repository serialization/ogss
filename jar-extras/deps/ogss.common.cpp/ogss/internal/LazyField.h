//
// Created by Timm Felden on 20.11.15.
//

#ifndef OGSS_CPP_COMMON_LAZYFIELD_H
#define OGSS_CPP_COMMON_LAZYFIELD_H

#include "DistributedField.h"
#include <map>

namespace ogss {
namespace internal {
class LazyField : public DistributedField {
    mutable streams::MappedInStream *input;

    inline bool isLoaded() { return nullptr == input; }

    void load();

  public:
    LazyField(FieldType *const type, api::String name, TypeID index,
              AbstractPool *const owner) :
      DistributedField(type, name, index, owner),
      input(nullptr) {}

    ~LazyField() override;

    inline void ensureIsLoaded() {
        if (!isLoaded())
            load();
    }

    void read(int i, int last, streams::MappedInStream &in) const override;

    virtual api::Box getR(const api::Object *i) override;

    virtual void setR(api::Object *i, api::Box v) override;

    virtual bool check() const override;
};
} // namespace internal
} // namespace ogss

#endif
