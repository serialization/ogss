//
// Created by Timm Felden on 20.11.15.
//

#include "LazyField.h"
#include "AbstractPool.h"

using namespace ogss;
using namespace internal;

api::Box LazyField::getR(const api::Object *i) {
    ObjectID ID = i->id;
    if (-1 == ID)
        return newData[i];

    ensureIsLoaded();
    if (--ID >= lastID)
        throw std::out_of_range("illegal access to distributed field");
    return data[ID - firstID];
}

void LazyField::setR(api::Object *i, api::Box v) {
    ObjectID ID = i->id;
    if (-1 == ID)
        newData[i] = v;

    ensureIsLoaded();
    if (--ID >= lastID)
        throw std::out_of_range("illegal access to distributed field");
    data[ID - firstID] = v;
}

void LazyField::load() {
    // we recycled first and last ID, so it is already set as intended
    const ObjectID high = lastID - firstID;
    ObjectID i = 0;
    data = new api::Box[high];
    while (i != high) {
        data[i++] = type->r(*input);
    }

    if (!input->eof())
        throw std::out_of_range("lazy read task did not consume InStream");

    delete input;
    input = nullptr;
}

void LazyField::read(int i, int last, ogss::streams::MappedInStream &in) const {
    this->firstID = i;
    this->lastID = last;
    this->input = &in;
}

bool LazyField::check() const {
    const_cast<LazyField *>(this)->ensureIsLoaded();
    return DistributedField::check();
}

LazyField::~LazyField() {
    delete input;
}
