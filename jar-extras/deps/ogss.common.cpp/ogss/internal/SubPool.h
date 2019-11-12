//
// Created by Timm Felden on 24.11.15.
//

#ifndef SKILL_CPP_COMMON_SUBPOOL_H
#define SKILL_CPP_COMMON_SUBPOOL_H

#include "../api/Builder.h"
#include "Pool.h"

namespace ogss {
namespace internal {

/**
 * A generic sub pool class that creates new objects via reflection to reduce
 * the amount of generated code.
 *
 * @author Timm Felden
 */
template <class T> class SubPool final : public Pool<T> {

    void allocateInstances() final {
        this->book = new Book<T>(this->staticDataInstances);
        T *page = this->book->firstPage();
        ObjectID i = this->bpo;
        const auto last = i + this->staticDataInstances;
        while (i < last) {
            const int j = i + 1;
            // note: the first page consist of fresh instances. So, placement
            // new is not required
            this->data[i] = new (page++) T(j, this);
            i = j;
        }
    }

    AbstractPool *
    makeSub(ogss::TypeID index, ogss::api::String name,
            std::unordered_set<TypeRestriction *> *restrictions) final {
        return new SubPool<T>(index, this, name, restrictions);
    }

  public:
    SubPool(TypeID TID, AbstractPool *super, ogss::api::String name,
            ::std::unordered_set<::ogss::restrictions::TypeRestriction *>
              *restrictions) :
      Pool<T>(TID, super, name, restrictions, 0) {}

    T *make() override {
        // book may not have been allocated yet
        if (!this->book) {
            this->book = new Book<T>();
        }
        T *rval = this->book->next();
        new (rval) T(-1, this);
        this->newObjects.push_back(rval);
        return rval;
    };

    /**
     * @return the most abstract builder to prevent users from using builders on
     * unknown types
     */
    api::Builder *build() final { return new api::Builder(this->make()); }
};
} // namespace internal
} // namespace ogss

#endif // SKILL_CPP_COMMON_SUBPOOL_H
