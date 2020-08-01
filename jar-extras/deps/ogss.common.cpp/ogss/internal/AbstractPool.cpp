//
// Created by Timm Felden on 04.11.15.
//

#include "AbstractPool.h"
#include "../api/File.h"
#include "../iterators/FieldIterator.h"
#include "../iterators/StaticFieldIterator.h"
#include "../iterators/TypeHierarchyIterator.h"
#include "AutoField.h"
#include "DataField.h"
#include "Pool.h"
#include "UnknownObject.h"

using namespace ogss;
using namespace internal;
using restrictions::TypeRestriction;

static AutoField **const noAutoFields = new AutoField *[0];

ogss::internal::AbstractPool::AbstractPool(
        TypeID TID, AbstractPool *superPool,
        api::String const name,
        std::unordered_set<TypeRestriction *> *restrictions,
        int afCount
) : FieldType(TID), restrictions(restrictions),
    name(name),
    super(superPool),
    base(superPool ? superPool->base : this),
    THH(superPool ? superPool->THH + 1 : 0),
    next(nullptr),
    dataFields(),
    afCount(afCount),
    autoFields(afCount ? new AutoField *[afCount] : noAutoFields) {

    for (int i = 0; i < afCount; i++)
        autoFields[i] = nullptr;
}

internal::AbstractPool::~AbstractPool() {
    delete restrictions;

    for (auto f : dataFields)
        delete f;

    if (afCount) {
        for (size_t i = 0; i < afCount; i++)
            delete autoFields[i];
        delete[] autoFields;
    }
}

api::Object *AbstractPool::getAsAnnotation(ObjectID id) const {
    // note: not using bpo as lower bound is in fact not correct but a
    // normalizing optimization
    return ((0 < id) & (id <= lastID))
             ? (((Pool<::ogss::api::Object> *)this)->data[id - 1])
             : nullptr;
}

ObjectID internal::AbstractPool::getObjectID(const api::Object *ref) const {
    if (!ref)
        return 0;

    const auto ID = ref->id;

    if (ID <= 0) {
        // new object
        auto dt = owner->pool(ref);

        // perform a subtype check
        // note: this is actually sufficient because we would end up with this
        // != dt and type(this) == type(dt) if ref had the correct type but
        // would belong to another state
        while (nullptr != dt && THH <= dt->THH) {
            if (this == dt)
                return ID;
            else
                dt = dt->super;
        }

        // from another type
        return 0;
    }

    // if ID is a valid index, we have to check if its the same object
    if (this->bpo < ID && ID <= this->lastID &&
        ref == ((Pool<::ogss::api::Object> *)this)->data[ID - 1]) {
        return ID;
    }

    // else its an object but not ours
    return 0;
}

api::Box AbstractPool::r(streams::InStream &in) const {
    api::Box r = {};
    const auto id = (ObjectID) (in.has(9) ? in.v64checked() : in.v64());
    r.anyRef = ((0 < id) & (id <= lastID))
               ? (((Pool<::ogss::api::Object> *) this)->data[id - 1])
               : nullptr;
    return r;
}

iterators::StaticFieldIterator internal::AbstractPool::fields() const {
    return iterators::StaticFieldIterator(this);
}

iterators::FieldIterator internal::AbstractPool::allFields() const {
    return iterators::FieldIterator(this);
}
