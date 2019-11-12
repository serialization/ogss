//
// Created by Timm Felden on 04.11.15.
//

#include "AbstractPool.h"
#include "UnknownObject.h"
#include "Pool.h"
#include "AutoField.h"
#include "DataField.h"
#include "../iterators/TypeHierarchyIterator.h"
#include "../iterators/FieldIterator.h"
#include "../iterators/StaticFieldIterator.h"

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
    return ((0 < id) & (id <= lastID))
           ? (((Pool<::ogss::api::Object> *) this)->data[id - 1])
           : nullptr;
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
