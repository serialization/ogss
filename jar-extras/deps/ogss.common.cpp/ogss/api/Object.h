//
// Created by Timm Felden on 06.11.15.
//

#ifndef OGSS_COMMON_OBJECT_H
#define OGSS_COMMON_OBJECT_H

#include "String.h"

namespace ogss {
namespace internal {
class DistributedField;

class FieldDeclaration;

class LazyField;

class AbstractPool;

template <class T> class Pool;

class Writer;
} // namespace internal
namespace fieldTypes {
class AnyRefType;
}
namespace api {
class File;

/**
 * an abstract skill object, i.e. the type of an annotation and the base type of
 * every known and unknown skill type instance.
 */
class Object {

  protected:
    /**
     * @invariant 0 <-> Deleted
     * @invariant id > 0 -> pool(this).data[id-1] == this
     * @invariant id < 0 -> pool(this).newObjects[-1-id] == this
     *
     * @note because pools of the same hierarchy share data, the second
     * invariant can be abused to access the object from any pool
     */
    ObjectID id;

    //! bulk allocation
    Object(){};

  public:
    /**
     * Only delete objects that you created explicitly via new. This is barely
     * ever the case.
     *
     * @note you must not free objects obtained from an iterator.
     */
    virtual ~Object() = default;

    /**
     * @return the static type ID
     * @note types with stid == -1 are named and hold a pointer to their pool
     */
    virtual TypeID stid() const = 0;

    /**
     * query, whether the object is marked for deletion and will be destroyed on
     * flush
     */
    bool isDeleted() const { return 0 == id; }

    friend class internal::AbstractPool;

    template <class T> friend class internal::Pool;

    template <typename T> friend struct std::hash;

    friend class internal::FieldDeclaration;

    friend class internal::DistributedField;

    friend class internal::LazyField;

    friend class internal::Writer;

    friend class fieldTypes::AnyRefType;

    friend class File;
};

/**
 * An Obj that holds a pointer to its pool.
 *
 * @author Timm Felden
 * @note This type definition is in internal, because we have to protect the
 * user from tampering with ID
 */
struct NamedObj {
    explicit NamedObj(const internal::AbstractPool *pool) : pool(pool) {}

    const internal::AbstractPool *const pool;
};
} // namespace api
} // namespace ogss

#endif // OGSS_COMMON_OBJECT_H
