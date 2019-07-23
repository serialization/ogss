//
// Created by Timm Felden on 28.01.16.
//

#include "DistributedField.h"
#include "AbstractPool.h"
#include "Pool.h"

using namespace ogss;
using namespace internal;

api::Box DistributedField::getR(const api::Object *i) {
    ObjectID ID = i->id;
    if (ID < 0)
        return newData[i];

    if (0 == ID || ID >= lastID)
        throw std::out_of_range("illegal access to distributed field");
    return data[ID - firstID];
}

void DistributedField::setR(api::Object *i, api::Box v) {
    ObjectID ID = i->id;
    if (ID < 0)
        newData[i] = v;

    if (0 == ID || ID >= lastID)
        throw std::out_of_range("illegal access to distributed field");
    data[ID - firstID] = v;
}

bool DistributedField::check() const {
    SK_TODO;
    //    if (checkedRestrictions.size()) {
    //        for (const auto &b : owner->blocks)
    //            for (auto i = b.bpo; i < b.bpo + b.dynamicCount; i++)
    //                for (auto r : checkedRestrictions)
    //                    if (!r->check(data[i]))
    //                        return false;
    //
    //        for (const auto &i : newData) {
    //            for (auto r : checkedRestrictions)
    //                if (!r->check(i.second))
    //                    return false;
    //        }
    //    }
    //    return true;
}

DistributedField::~DistributedField() { delete[] data; }

//! global lock used to synchronize allocations of data
static std::mutex dataLock;

void DistributedField::read(int begin, const int end,
                            streams::MappedInStream &in) const {
    // we fill in data and data is nullptr at this point, so we have to allocate
    // it first
    if (nullptr == data) {
        std::lock_guard<std::mutex> m(dataLock);
        if (nullptr == data) {
            data = new api::Box[lastID - firstID];
        }
    }

    const auto high = end - firstID;
    auto i = begin - firstID;
    while (i != high) {
        data[++i] = type->r(in);
    }
}

bool DistributedField::write(int begin, const int end,
                             streams::BufferedOutStream *out) const {
    bool drop = true;
    const auto high = end - firstID;
    auto i = begin - firstID;
    while (i != high) {
        drop &= type->w(data[++i], out);
    }
    return drop;
}

/**
 * @note this method is invoked _before_ object IDs get reassigned!
 */
void DistributedField::compress(const ObjectID newLBPO) const {
    // create new data
    api::Box *d = new api::Box[owner->cachedSize];

    // calculate new data
    // note: data could be null
    ObjectID next = 0;
    if (data) {
        auto is = owner->allObjects();
        while (is->hasNext()) {
            const Object *const i = is->next();
            const ObjectID ID = i->id;
            if (0 != ID) {
                d[next++] = ID < 0 ? newData[i] : data[ID - firstID];
            }
        }
    } else {
        auto is = owner->allObjects();
        while (is->hasNext()) {
            const Object *const i = is->next();
            const ObjectID ID = i->id;
            if (0 != ID) {
                d[next++] = ID < 0 ? newData[i] : api::Box{};
            }
        }
    }

    // update state
    delete[] data;
    data = d;
    firstID = newLBPO + 1;
    lastID = firstID + owner->cachedSize;
    assert(next == owner->cachedSize);

    newData.clear();
}
