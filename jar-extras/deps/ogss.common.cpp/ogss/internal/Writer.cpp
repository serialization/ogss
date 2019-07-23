//
// Created by Timm Felden on 05.04.19.
//

#include <future>

#include "../api/File.h"
#include "../fieldTypes/HullType.h"
#include "../fieldTypes/MapType.h"
#include "../fieldTypes/SingleArgumentType.h"
#include "../streams/FileOutputStream.h"

#include "DataField.h"
#include "DistributedField.h"
#include "EnumPool.h"
#include "Pool.h"
#include "Writer.h"

using namespace ogss::internal;
using ogss::fieldTypes::HullType;
using ogss::streams::BufferedOutStream;

Writer::Writer(api::File *state, streams::FileOutputStream &out) :
  resultLock(),
  results(),
  errors() {
    /**
     * *************** * G * ****************
     */

    if (state->guard.empty()) {
        out.i16((short)0x2622);
    } else {
        out.i8('#');
        out.put(&state->guard);
        out.i8(0);
    }

    /**
     * *************** * S * ****************
     */

    // prepare string pool
    {
        StringPool *const sp = (StringPool *)state->strings;
        sp->resetIDs();

        // create inverse in
        const int count = sp->knownStrings.size();
        sp->knownStrings.clear();
        sp->idMap.reserve(count);
        sp->IDs.reserve(count);
        for (size_t i = 0; i < sp->literalStringCount; i++) {
            const String s = sp->literalStrings[i];
            sp->IDs[s] = sp->idMap.size();
            sp->idMap.push_back((void *)s);
        }
        sp->hullOffset = sp->idMap.size();
    }
    // write in parallel to writing of TF
    auto SB = std::async(std::launch::async, StringPool::writeLiterals,
                         (StringPool *)state->strings, &out);

    /**
     * *************** * T F * ****************
     */
    int awaitBuffers;
    {
        // write T and F to a buffer, while S is written
        BufferedOutStream *const buffer = new BufferedOutStream();

        // @note here, the field data write tasks will be started already
        awaitBuffers = writeTF(state, *buffer);
        SB.get();

        // write buffered TF-blocks
        out.write(buffer);
    }

    /**
     * *************** * HD * ****************
     */

    // await data from all HD tasks
    bool hasErrors = false;
    size_t i = 0;
    for (; awaitBuffers != 0; awaitBuffers--, i++) {
        std::future<BufferedOutStream *> *f = nullptr;
        {
            std::lock_guard<std::mutex> consumerLock(resultLock);
            if (!errors.empty()) {
                // if a task crashed, we will inevitably notice it here,
                // because sending its buffer is its last action
                hasErrors = true;
                // @note there is still a data race between successful threads
                // and crashing threads that may lead to a crash; it would
                // become harmless, if we get rid of resultLock, because Writer
                // is stack-local
                // @note we could also set a shutdown flag, if this is a serious
                // problem
                break;
            }

            f = &results.at(i);
        }
        BufferedOutStream *const buf = f->get();
        if (buf) {
            out.writeSized(buf);
        }
        // else: some buffer was discarded
    }

    // report errors
    if (hasErrors) {
        std::lock_guard<std::mutex> consumerLock(resultLock);
        // await remaining buffers to prevent crashes
        for (; awaitBuffers != 0; awaitBuffers--, i++) {
            if (i < results.size()) {
                auto buf = results.at(i).get();
                // delete remaining successful buffers
                if (buf)
                    delete buf;
            }
        }

        std::stringstream ss;
        ss << "write failed:";
        for (auto &msg : errors) {
            ss << "\n  " << msg;
        }
        throw ogss::Exception(ss.str());
    }
}

static inline void attr(AbstractPool *const p, BufferedOutStream &out) {
    out.i8(0);
}

static inline void attr(FieldDeclaration *const p, BufferedOutStream &out) {
    out.i8(0);
}

uint32_t Writer::writeTF(api::File *const state, BufferedOutStream &out) {
    uint32_t awaitHulls = 0;

    std::vector<DataField *> fieldQueue;
    StringPool *const string = (StringPool *)state->strings;

    /**
     * *************** * T Class * ****************
     */

    // calculate new bpos, sizes, object IDs and compress data arrays
    {
        std::vector<std::future<void>> barrier;

        const int classCount = state->classCount;
        int *const bpos = new int[classCount];
        for (int i = 0; i < classCount; i++) {
            AbstractPool *const p = state->classes[i];
            if (nullptr == p->super) {
                barrier.push_back(
                  std::async(std::launch::async, compress, p, bpos));
            }
        }

        // write count of the type block
        out.v64(classCount);

        // initialize local state before waiting for compress
        fieldQueue.reserve(2 * classCount);

        // await jobs
        for (auto &f : barrier)
            f.get();

        delete[] bpos;

        // write types
        for (int i = 0; i < classCount; i++) {
            AbstractPool *const p = state->classes[i];
            out.v64(string->id(p->name));
            out.v64(p->staticDataInstances);
            attr(p, out);
            if (nullptr == p->super)
                out.i8(0);
            else {
                // superID
                out.v64(p->super->typeID - 9);
                // our bpo
                out.v64(p->bpo);
            }

            out.v64((int)p->dataFields.size());

            // add field to queues for description and data tasks
            for (DataField *f : p->dataFields) {
                fieldQueue.push_back(f);
            }
        }
    }

    /**
     * *************** * T Container * ****************
     */

    // write count of the type block
    {
        const int containerCount = state->containerCount;

        // number of containers written to disk
        uint32_t count = 0;

        // set deps and calculate count
        for (int i = 0; i < containerCount; i++) {
            HullType *const c = state->containers[i];
            if (c->maxDeps != 0) {
                c->resetIDs();
                c->deps.store(c->maxDeps);
                count++;
            }
        }
        if (string->maxDeps != 0) {
            awaitHulls = 1;
            string->deps.store(string->maxDeps);
        }
        awaitHulls += count;

        out.v64((int)count);
        for (int i = 0; i < containerCount; i++) {
            HullType *const c = state->containers[i];
            if (c->maxDeps != 0) {
                c->writeDecl(out);
            }
        }
    }

    // note: we cannot start field jobs immediately because they could decrement
    // deps to 0 multiple times in that case
    {
        std::lock_guard<std::mutex> rLock(resultLock);
        // C++ bullshit: we have to reserve space for futures, because they
        // would be deleted on resize :-(
        results.reserve(fieldQueue.size() + awaitHulls);
        for (DataField *f : fieldQueue) {
            results.emplace_back(
              std::async(std::launch::async, writeField, this, f));
        }
    }

    /**
     * *************** * T Enum * ****************
     */

    if (state->enumCount) {
        // write count of the type block
        out.v64((int)state->enumCount);
        for (int i = 0; i < state->enumCount; i++) {
            internal::AbstractEnumPool *p = state->enums[i];
            out.v64(string->id(p->name));
            out.v64((int)((EnumPool<api::UnknownEnum> *)p)->values.size());
            for (AbstractEnumProxy *v : *p) {
                out.v64(string->id(v->name));
            }
        }
    } else
        out.i8(0);

    /**
     * *************** * F * ****************
     */

    for (DataField *f : fieldQueue) {
        // write info
        out.v64(string->id(f->name));
        out.v64(f->type->typeID);
        attr(f, out);
    }

    out.close();

    // fields + hull types
    return fieldQueue.size() + awaitHulls;
}

void Writer::compress(AbstractPool *const base, int *bpos) {
    // create our part of the bpo in
    {
        int next = 0;
        AbstractPool *p = base;

        do {
            bpos[p->typeID - 10] = next;
            const int s = p->staticSize() - p->deletedCount;
            p->cachedSize = s;
            next += s;
            p = p->next;
        } while (p);
    }

    // calculate correct dynamic size for all sub pools (in reverse order)
    {
        // @note this can only happen, if there is a class
        AbstractPool *const *cs =
          base->owner->classes + base->owner->classCount - 1;
        AbstractPool *p;
        while (base != (p = *(cs--))) {
            if (base == p->base) {
                p->super->cachedSize += p->cachedSize;
            }
        }
    }

    // reset layout of distributed fields
    {
        AbstractPool *p = base;
        while (p) {
            for (DataField *f : p->dataFields) {
                if (auto df = dynamic_cast<DistributedField *>(f)) {
                    df->compress(bpos[p->typeID - 10]);
                }
            }
            p = p->next;
        }
    }

    // from now on, size will take deleted objects into account, thus d may
    // in fact be smaller then data!
    Object **tmp = ((Pool<Object> *)base)->data;
    base->allocateData();
    Object **d = ((Pool<Object> *)base)->data;
    ((Pool<Object> *)base)->data = tmp;
    ObjectID pos = 0;

    {
        auto is = base->allObjects();
        while (is->hasNext()) {
            Object *const i = is->next();
            if (0 != i->id) {
                d[pos] = i;
                i->id = ++pos;
            } else {
                ((Pool<Object> *)base->owner->pool(i))->book->free(i);
            }
        }
    }
    delete[] tmp;

    // update after compress for all sub-pools
    AbstractPool *p = base;

    do {
        p->resetOnWrite(d);
        p->bpo = bpos[p->typeID - 10];
    } while ((p = p->next));
}

BufferedOutStream *Writer::writeField(Writer *self, DataField *f) {
    BufferedOutStream *buffer = new BufferedOutStream();

    bool discard = true;

    try {
        AbstractPool *owner = f->owner;
        int i = owner->bpo;
        int h = i + owner->cachedSize;

        // any empty field will be discarded
        if (i != h) {
            buffer->v64(f->fieldID);
            discard = f->write(i, h, buffer);
        }

        if (auto ht = dynamic_cast<HullType *>((FieldType *)f->type)) {
            if (0 == --ht->deps) {
                std::lock_guard<std::mutex> rLock(self->resultLock);
                self->results.emplace_back(
                  std::async(std::launch::async, writeHull, self, ht));
            }
        }
    } catch (std::exception &e) {
        std::lock_guard<std::mutex> errLock(self->resultLock);

        self->errors.emplace_back(e.what());
    } catch (...) {
        self->errors.emplace_back("write task non-standard crash");
    }

    // close buffer and discard it if possible
    buffer->close();
    if (discard) {
        delete buffer;
        return nullptr;
    }

    return buffer;
}

BufferedOutStream *Writer::writeHull(Writer *self, HullType *t) {
    BufferedOutStream *buffer = new BufferedOutStream();

    bool discard = true;

    try {
        buffer->v64(t->fieldID);
        discard = t->write(buffer);

        if (auto p = dynamic_cast<fieldTypes::SingleArgumentType *>(t)) {
            if (auto bt = dynamic_cast<HullType *>(p->base)) {
                if (0 == --bt->deps) {
                    std::lock_guard<std::mutex> rLock(self->resultLock);
                    self->results.push_back(
                      std::async(std::launch::async, writeHull, self, bt));
                }
            }
        } else if (dynamic_cast<StringPool *>(t)) {
            // nothing to do (in fact we cant type check a MapType)
        } else {
            fieldTypes::MapType<api::Box, api::Box> *p =
              (fieldTypes::MapType<Box, Box> *)t;
            if (auto bt = dynamic_cast<HullType *>(p->keyType)) {
                if (0 == --bt->deps) {
                    std::lock_guard<std::mutex> rLock(self->resultLock);
                    self->results.push_back(
                      std::async(std::launch::async, writeHull, self, bt));
                }
            }
            if (auto bt = dynamic_cast<HullType *>(p->valueType)) {
                if (0 == --bt->deps) {
                    std::lock_guard<std::mutex> rLock(self->resultLock);
                    self->results.push_back(
                      std::async(std::launch::async, writeHull, self, bt));
                }
            }
        }
    } catch (std::exception &e) {
        std::lock_guard<std::mutex> errLock(self->resultLock);

        self->errors.push_back(e.what());
    } catch (...) {
        self->errors.push_back("write task non-standard crash");
    }

    // close buffer and discard it if possible
    buffer->close();
    if (discard) {
        delete buffer;
        return nullptr;
    }

    return buffer;
}
