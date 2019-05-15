//
// Created by Timm Felden on 20.11.15.
//

#include "LazyField.h"
#include "AbstractPool.h"

using namespace ogss;
using namespace internal;

api::Box LazyField::getR(const api::Object *i) {
    if (-1 == i->id)
        return newData[i];

    ensureIsLoaded();
    return data[i->id - 1];
}

void LazyField::setR(api::Object *i, api::Box v) {
    if (-1 == i->id)
        newData[i] = v;

    ensureIsLoaded();
    data[i->id - 1] = v;
}

void LazyField::load() {
    SK_TODO;

    //    if (!data.p)
    //        new(&data) streams::SparseArray<api::Box>((size_t) owner->base->size(),
    //                                                  owner->blocks.size() <= 1 && !owner->blocks[0].bpo);
    //
    //    for (const auto &e : *parts) {
    //        const auto &target = e.first;
    //        auto &part = e.second;
    //        ogss::streams::MappedInStream &in = *part;
    //
    //        try {
    //            if (dynamic_cast<const SimpleChunk *>(target)) {
    //                for (::ogss::ObjectID i = ((const ::ogss::internal::SimpleChunk *) target)->bpo,
    //                             high = i + target->count; i != high; i++)
    //                    data[i] = type->r(in);
    //            } else {
    //                //case bci : BulkChunk ⇒
    //                for (int i = 0; i < ((const ::ogss::internal::BulkChunk *) target)->blockCount; i++) {
    //                    const auto &b = owner->blocks[i];
    //                    for (::ogss::ObjectID i = b.bpo, end = i + b.dynamicCount; i != end; i++)
    //                        data[i] = type->r(in);
    //                }
    //            }
    //        } catch (::ogss::Exception& e) {
    //            throw ParseException(
    //                    in.getPosition(),
    //                    part->getPosition() + target->begin,
    //                    part->getPosition() + target->end, e.what());
    //        } catch (...) {
    //            throw ParseException(
    //                    in.getPosition(),
    //                    part->getPosition() + target->begin,
    //                    part->getPosition() + target->end, "unexpected foreign exception");
    //        }
    //
    //        if (!in.eof())
    //            throw ParseException(
    //                    in.getPosition(),
    //                    part->getPosition() + target->begin,
    //                    part->getPosition() + target->end, "did not consume all bytes");
    //        delete part;
    //    }
    //    delete parts;
    //    parts = nullptr;
}

bool LazyField::check() const {
    const_cast<LazyField *>(this)->ensureIsLoaded();
    return DistributedField::check();
}

LazyField::~LazyField() {
    if (input) {
        delete input;
    }
}
