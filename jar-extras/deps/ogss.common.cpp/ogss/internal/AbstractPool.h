//
// Created by Timm Felden on 04.11.15.
//

#ifndef SKILL_CPP_COMMON_ABSTRACTSTORAGEPOOL_H
#define SKILL_CPP_COMMON_ABSTRACTSTORAGEPOOL_H

#include <assert.h>
#include <vector>

#include "../common.h"
#include "../fieldTypes/BuiltinFieldType.h"
#include "../fieldTypes/FieldType.h"
#include "../restrictions/TypeRestriction.h"
#include "../utils.h"
#include "StringPool.h"

namespace ogss {
namespace api {
class Builder;

class File;
} // namespace api

namespace iterators {
class AllObjectIterator;

template <class T> class DynamicDataIterator;

class TypeHierarchyIterator;

class StaticFieldIterator;

class FieldIterator;
} // namespace iterators

using restrictions::TypeRestriction;
namespace internal {

class AutoField;
class DataField;
class DistributedField;

class Creator;

class Parser;
class ParParser;
class ParReadTask;
class SeqParser;
class SeqReadTask;

class Writer;

/**
 * this class reflects all storage pool properties, that do not depend on types
 *
 * @note let us assume, that the Abstract storage pool is of type T and its base
 * pool is of type B
 */
class AbstractPool : public fieldTypes::FieldType {
  protected:
    AbstractPool(TypeID typeID, AbstractPool *superPool, api::String name,
                 std::unordered_set<TypeRestriction *> *restrictions,
                 int afCount);

  public:
    virtual ~AbstractPool();

  private:
    virtual ObjectID newObjectsSize() const = 0;

  protected:
    /**
     * number of deleted instances currently stored in this pool
     */
    ObjectID deletedCount = 0;

  private:
    /**
     * Delete shall only be called from skill state
     *
     * @param target
     *            the object to be deleted
     * @note we type target using the erasure directly, because the C++ type
     * system is too weak to express correct typing, when taking the pool from a
     * map
     */
    inline void free(api::Object *target) {
        // @note we need no null or 0 check, because both happen in SkillState
        target->id = 0;
        deletedCount++;
    }

  protected:
    /**
     * ensure that data is set correctly
     *
     * @note internal use only!
     */
    virtual void allocateData() = 0;

    /**
     * ensure that instances are created correctly
     *
     * @note internal use only!
     * @note will parallelize over blocks and can be invoked in parallel
     */
    virtual void allocateInstances() = 0;

  public:
    /**
     * returns an instance by ObjectID, without further knowledge of the type
     * of the returned instance.
     *
     * @note if there is static knowledge that the pool is of type T, a cast to
     * T* is always safe
     */
    api::Object *getAsAnnotation(ObjectID id) const;

    /**
     * @return a new instance with default field values
     */
    virtual api::Object *make() = 0;

    /**
     * @return a builder that constructs a new instance registered in this pool
     */
    virtual api::Builder *build() = 0;

    /**
     * restrictions of this pool
     */
    std::unordered_set<TypeRestriction *> *const restrictions;

    /**
     * the name of this pool
     */
    const api::String name;

    /**
     * in fact a Pool[? >: T]
     */
    AbstractPool *const super;

    /**
     * in fact a Pool[? >: T]
     */
    AbstractPool *const base;

  private:
    /**
     * distance to the base type
     */
    const int THH;

    /**
     * a pointer to the next pool in a type order traversal
     */
    AbstractPool *next;

  protected:
    api::File *owner;

  public:
    inline api::File *getOwner() const { return owner; }

  protected:
    /**
     * Get the name of known sub pool with argument local id. Return null, if id
     * is invalid.
     */
    virtual api::String nameSub(uint32_t id) const { return nullptr; }

    /**
     * Create the known sub pool with argument local id. Return null, if id is
     * invalid.
     */
    virtual AbstractPool *
    makeSub(uint32_t id, TypeID index,
            std::unordered_set<TypeRestriction *> *restrictions) {
        return nullptr;
    }

    /**
     * create an anonymous subtype
     */
    virtual AbstractPool *
    makeSub(ogss::TypeID typeID, ogss::api::String name,
            std::unordered_set<TypeRestriction *> *restrictions) = 0;

    ObjectID cachedSize = 0;

    //! internal use only
    ObjectID staticDataInstances = 0;
    /**
     * The BPO of this pool relative to data.
     */
    int bpo;

    //! reset internal state on write
    virtual void resetOnWrite(api::Object **d) = 0;

  private:
    /**
     * The last valid ID (used to check get out of bounds errors)
     */
    int lastID;

  public:
    /**
     * the size of this pool including new object, but excluding subpools
     */
    ObjectID staticSize() const {
        return staticDataInstances + newObjectsSize();
    }

    /**
     * Returns instances as api::Object*s; required for reflection
     */
    virtual std::unique_ptr<iterators::AllObjectIterator>
    allObjects() const = 0;

    /**
     * the size of this pool, including subpools and new objects
     */
    ObjectID size() const {
        int size = 0;
        const AbstractPool *p = this;
        const auto endTHH = THH;
        do {
            size += p->staticSize();
            p = p->next;
        } while (p && endTHH < p->THH);

        return size;
    }

    iterators::StaticFieldIterator fields() const;

    iterators::FieldIterator allFields() const;

  protected:
    std::vector<DataField *> dataFields;
    size_t afCount;
    AutoField **const autoFields;

    /**
     * names of known fields, the actual field information is given in the
     * generated addKnownFiled method.
     */
    virtual api::String KFN(uint32_t id) const { return nullptr; }

    /**
     * construct the known field with the given id
     */
    virtual FieldDeclaration *KFC(uint32_t id, FieldType **SIFA,
                                  TypeID nextFID) {
        return nullptr;
    }

    api::Box r(streams::InStream &in) const final;

    bool w(api::Box target, streams::BufferedOutStream *out) const final {
        const auto v = target.anyRef;
        if (v) {
          out->v64(target.anyRef->id);
          return false;
        } else {
          out->i8(0);
          return true;
        }
    }

    friend class AutoField;
    friend class DataField;
    friend class DistributedField;

    friend class Creator;

    friend class Parser;
    friend class ParParser;
    friend class ParReadTask;
    friend class SeqParser;
    friend class SeqReadTask;

    friend class Writer;

    friend class api::File;

    friend class iterators::TypeHierarchyIterator;

    template <class T> friend class iterators::DynamicDataIterator;

    friend class iterators::FieldIterator;

    friend class iterators::StaticFieldIterator;
};
} // namespace internal
} // namespace ogss

#endif // SKILL_CPP_COMMON_ABSTRACTSTORAGEPOOL_H
