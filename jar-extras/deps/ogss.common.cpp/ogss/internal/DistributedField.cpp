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


DistributedField::~DistributedField() {
    delete[] data;
}


void DistributedField::read(int i, const int last, streams::MappedInStream &in) const {
    // we fill in data and data is nullptr at this point, so we have to allocate it first
    firstID = i;
    lastID = last;
    const int high = last - i;
    i = 0;
    data = new api::Box[high];
    while (i != high) {
        data[i++] = type->r(in);
    }
}

bool DistributedField::write(int i, const int last, streams::BufferedOutStream *out) const {
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
 *
 * @todo append is not implemented, because there is no way to test it, yet.
 *
 * @todo implementation of append may require a second parameter (bool isAppend)
 *
 * @todo ignores deletes!
 */
//void DistributedField::resetChunks(ObjectID lbpo, ObjectID newSize) {
// note to self: data could be null

//    // @note we cannot delete objects and we will always write
//    // therefore, new IDs will be data ++ newData matching exactly the pool's last block
//
//
//    // update internal (requires old block structure, so that's the first action)
//    {
//        // create new data on the stack
//        api::Box *d = new api::Box[lbpo + newSize];
//
//        // update data -> d
//        size_t newI = (size_t) lbpo;
//        {
//            // todo subtract deleted objects on implementation of delete
//            size_t newEnd = (size_t) (lbpo);
//            for (const auto *chunk : dataChunks) {
//                newEnd += chunk->count;
//            }
//            auto iter = ((Pool<Object> *) owner)->allInTypeOrder();
//            while (newI < newEnd) {
//                assert(iter.hasNext());
//                d[newI++] = data[iter.next()->id - 1];
//            }
//        }
//
//        // update newData -> d
//        {
//            const size_t newEnd = (size_t) (lbpo + newSize);
//            while (newI < newEnd) {
//                d[newI] = newData[owner->getAsAnnotation(newI)];
//                newI++;
//            }
//            newData.clear();
//        }
//
//        // move d -> data
//        SK_TODO;
//        // data.resize(d);
//    }
//
//    SK_TODO;
//    //    FieldDeclaration::resetChunks(newSize, 0);
//}
