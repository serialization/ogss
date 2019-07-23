//
// Created by Timm Felden on 20.11.15.
//

#include "LazyField.h"
#include "AbstractPool.h"

using namespace ogss;
using namespace internal;

api::Box LazyField::getR(const api::Object *i) {
    ObjectID ID = i->id;
    if (ID < 0)
        return newData[i];

    if (0 == ID || ID >= lastID)
        throw std::out_of_range("illegal access to lazy field");

    ensureIsLoaded();

    return data[ID - firstID];
}

void LazyField::setR(api::Object *i, api::Box v) {
    ObjectID ID = i->id;
    if (ID < 0)
        newData[i] = v;

    if (0 == ID || ID >= lastID)
        throw std::out_of_range("illegal access to lazy field");

    ensureIsLoaded();

    data[ID - firstID] = v;
}

void LazyField::load() {
    for (Chunk &c : *chunks) {
        DistributedField::read(c.begin, c.end, *c.in);

        if (!c.in->eof())
            throw std::out_of_range("lazy read task did not consume InStream");

        delete c.in;
    }

    delete chunks;
    chunks = nullptr;
}

//! global lock used to synchronize deferred reads
static std::mutex readLock;

void LazyField::read(int i, int last, ogss::streams::MappedInStream &in) const {
    std::lock_guard<std::mutex> m(readLock);

    if (!chunks) {
        chunks = new std::vector<Chunk>;
    }
    chunks->emplace_back(Chunk{i, last, &in});
}

bool LazyField::check() const {
    const_cast<LazyField *>(this)->ensureIsLoaded();
    return DistributedField::check();
}

LazyField::~LazyField() {
    if (chunks) {
        for (Chunk &c : *chunks) {
            delete c.in;
        }
        delete chunks;
    }
}
