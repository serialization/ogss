//
// Created by Timm Felden on 04.11.15.
//

#ifndef OGSS_COMMON_STORAGEPOOL_H
#define OGSS_COMMON_STORAGEPOOL_H

#include "../iterators/AllObjectIterator.h"
#include "../iterators/DynamicDataIterator.h"
#include "../iterators/TypeOrderIterator.h"
#include "../restrictions/TypeRestriction.h"
#include "AbstractPool.h"
#include "Book.h"

namespace ogss {
namespace api {
class File;
}
namespace iterators {
template <class T> class StaticDataIterator;

template <class T> class DynamicDataIterator;
} // namespace iterators

using restrictions::TypeRestriction;
namespace internal {
template <class T> class SubPool;

class Writer;

/**
 * @author Timm Felden
 *
 * @tparam T the type of the represented class, i.e. without a *
 */
template <class T> class Pool : public AbstractPool {
  public:
    /**
     * @note internal use only!
     */
    T **data;

  protected:
    /**
     * allocated when all instances are allocated, because by then, we can know
     * how many instances are to be read from file, which is quite helpful
     */
    Book<T> *book;

    void allocateData() final {
        if (super) {
            this->data = (T **)((Pool<T> *)this->base)->data;
        } else {
            this->data = new T *[this->cachedSize];
        }
    }

    void resetOnWrite(ogss::api::Object **d) final {
        // update data
        data = (T **)d;

        // update structural knowledge of data
        staticDataInstances += newObjects.size() - deletedCount;
        deletedCount = 0;
        newObjects.clear();
    }

    void allocateInstances() override {
        book = new Book<T>(staticDataInstances);
        T *page = book->firstPage();
        ObjectID i = bpo;
        const auto last = i + staticDataInstances;
        while (i < last) {
            data[i] = new (page) T();
            (page++)->id = ++i;
        }
    }

    /**
     * All stored objects, which have exactly the type T. Objects are stored as
     * arrays of field entries. The types of the respective fields can be
     * retrieved using the fieldTypes map.
     */
    std::vector<T *> newObjects;

    ObjectID newObjectsSize() const override {
        return (ObjectID)newObjects.size();
    }

    Pool(TypeID TID, AbstractPool *superPool, api::String name,
         std::unordered_set<TypeRestriction *> *restrictions, int autoFields) :
      AbstractPool(TID, superPool, name, restrictions, autoFields),
      data(nullptr),
      book(nullptr),
      newObjects() {}

    virtual ~Pool() {
        if (book)
            delete book;
        if (!super)
            delete[] data;
    }

  public:
    inline T *get(ObjectID id) const {
        // TODO check upper bound
        return id <= 0 ? nullptr : data[id - 1];
    }

    T *make() override {
        if (!book)
            book = new Book<T>();

        T *rval = book->next();
        new (rval) T();
        rval->id = -1 - this->newObjects.size();
        this->newObjects.push_back(rval);
        return rval;
    };

    std::unique_ptr<iterators::AllObjectIterator> allObjects() const final {
        return std::unique_ptr<iterators::AllObjectIterator>(
          new iterators::AllObjectIterator::Implementation<T>(this));
    }

    iterators::StaticDataIterator<T> staticInstances() const {
        return iterators::StaticDataIterator<T>(this);
    };

    iterators::DynamicDataIterator<T> all() const {
        return iterators::DynamicDataIterator<T>(this);
    };

    iterators::TypeOrderIterator<T> allInTypeOrder() const {
        return iterators::TypeOrderIterator<T>(this);
    };

    iterators::DynamicDataIterator<T> begin() const {
        return iterators::DynamicDataIterator<T>(this);
    };

    const iterators::DynamicDataIterator<T> end() const {
        return iterators::DynamicDataIterator<T>();
    }

    friend class AbstractPool;

    friend class Writer;

    friend class api::File;

    //! static data iterator can traverse over new objects
    friend class iterators::StaticDataIterator<T>;

    //! dynamic data iterator can traverse over new objects
    friend class iterators::DynamicDataIterator<T>;
};
} // namespace internal
} // namespace ogss

#endif // OGSS_COMMON_STORAGEPOOL_H
