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
    if (-1 == ID)
        return newData[i];

    if (--ID >= lastID)
        throw std::out_of_range("illegal access to distributed field");
    return data[ID - firstID];
}

void DistributedField::setR(api::Object *i, api::Box v) {
    ObjectID ID = i->id;
    if (-1 == ID)
        newData[i] = v;

    if (--ID >= lastID)
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

void DistributedField::read(int i, const int last,
                            streams::MappedInStream &in) const {
    // we fill in data and data is nullptr at this point, so we have to allocate
    // it first
    firstID = i;
    lastID = last;
    const int high = last - i;
    i = 0;
    data = new api::Box[high];
    while (i != high) {
        data[i++] = type->r(in);
    }
}

bool DistributedField::write(int i, const int last,
                             streams::BufferedOutStream *out) const {
    bool drop = true;
    if (auto bt = dynamic_cast<const fieldTypes::BoolFieldType *>(type)) {
        SK_TODO;
        //        BoolOutWrapper wrap = new BoolOutWrapper(out);
        //        for (; i < h; i++) {
        //            boolean v = Boolean.TRUE == data.get(d[i]);
        //            wrap.
        //            bool(v);
        //            drop &= !v;
        //        }
        //        wrap.unwrap();
    } else {
        // it is always data and therefore shifted
        const ObjectID high = last - i;
        i = 0;
        while (i < high) {
            drop &= type->w(data[i++], out);
        }
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
            ObjectID ID = i->id;
            if (0 != ID) {
                d[next++] = i->id < 0 ? newData[i] : data[--ID - firstID];
            }
        }
    } else {
        auto is = owner->allObjects();
        while (is->hasNext()) {
            const Object *const i = is->next();
            if (0 != i->id) {
                d[next++] = i->id < 0 ? newData[i] : api::Box{};
            }
        }
    }

    // update state
    delete[] data;
    data = d;
    firstID = newLBPO;
    lastID = firstID + owner->cachedSize;
    assert(next == owner->cachedSize);

    newData.clear();
}
