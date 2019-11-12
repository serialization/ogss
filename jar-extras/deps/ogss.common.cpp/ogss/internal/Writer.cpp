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
  errors(),
  awaitBuffers(0) {
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
            const ::ogss::api::String s = sp->literalStrings[i];
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
            // if the writer is faster then encoders triggering hull fields, it
            // could be that we have to await buffers which have not even been
            // enqueued yet
            while (true) {
                {
                    std::lock_guard<std::mutex> consumerLock(resultLock);
                    if (!errors.empty()) {
                        // if a task crashed, we will inevitably notice it here,
                        // because sending its buffer is its last action
                        hasErrors = true;
                        // @note there is still a data race between successful
                        // threads and crashing threads that may lead to a
                        // crash; it would become harmless, if we get rid of
                        // resultLock, because Writer is stack-local
                        // @note we could also set a shutdown flag, if this is a
                        // serious problem
                        break;
                    }
                    if (i < results.size())
                        break;
                }
                std::this_thread::yield();
            }
            if (hasErrors)
                break;

            std::lock_guard<std::mutex> consumerLock(resultLock);
            f = &results[i];
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
              std::async(std::launch::async, writeField, this, f, 0));
        }
    }

    /**
     * *************** * T Enum * ****************
     */

    if (state->enumCount) {
        // write count of the type block
        out.v64((int)state->enumCount);
        for (size_t i = 0; i < state->enumCount; i++) {
            internal::AbstractEnumPool *p = state->enums[i];
            out.v64(string->id(p->name));
            out.v64((int)((EnumPool<api::UnknownEnum> *)p)->values.size());
            for (::ogss::api::AbstractEnumProxy *v : *p) {
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
    ::ogss::api::Object **tmp = ((Pool<::ogss::api::Object> *)base)->data;
    base->allocateData();
    ::ogss::api::Object **d = ((Pool<::ogss::api::Object> *)base)->data;
    ((Pool<::ogss::api::Object> *)base)->data = tmp;
    ObjectID pos = 0;

    {
        auto is = base->allObjects();
        while (is->hasNext()) {
        	::ogss::api::Object *const i = is->next();
            if (0 != i->id) {
                d[pos] = i;
                i->id = ++pos;
            } else {
                ((Pool<::ogss::api::Object> *)base->owner->pool(i))->book->free(i);
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

BufferedOutStream *Writer::writeField(Writer *self, DataField *f,
                                      BlockID block) {
    try {
        const auto owner = f->owner;
        const auto count = owner->cachedSize;

        bool hasblocks = false;
        BufferedOutStream *buffer = nullptr;

        // any empty field will be discarded
        if (count != 0) {

            // iff we have blockID zero we may need to split
            if (0 == block) {
                // split large FD blocks into blocks
                if (count > ogss::FD_Threshold) {
                    hasblocks = true;

                    // we have to fork this task
                    int blockCount = (count - 1) / ogss::FD_Threshold;
                    // @note we increment await by blockCount - 1
                    self->awaitBuffers += blockCount++;

                    f->blocks = blockCount;

                    std::lock_guard<std::mutex> rLock(self->resultLock);
                    for (int i = 1; i < blockCount; i++) {
                        self->results.emplace_back(std::async(
                          std::launch::async, writeField, self, f, i));
                    }
                }
            } else {
                hasblocks = true;
            }

            const auto bpo = owner->bpo;
            int i = block * ogss::FD_Threshold;
            int h = std::min(count, i + ogss::FD_Threshold);
            i += bpo;
            h += bpo;

            buffer = new BufferedOutStream();

            buffer->v64(f->fieldID);
            if (count > ogss::FD_Threshold) {
                buffer->v64(block);
            }
            bool discard = f->write(i, h, buffer);

            // close buffer and discard it if possible
            buffer->close();
            if (discard) {
                delete buffer;
                buffer = nullptr;
            }
        }

        bool done = true;
        if (hasblocks) {
            done = 0 == --f->blocks;
        }

        if (done) {
            if (auto ht = dynamic_cast<const HullType *>(f->type)) {
                if (0 == --ht->deps) {
                    std::lock_guard<std::mutex> rLock(self->resultLock);
                    self->results.emplace_back(
                      std::async(std::launch::async, writeHull, self, ht, 0));
                }
            }
        }

        return buffer;
    } catch (std::exception &e) {
        std::lock_guard<std::mutex> errLock(self->resultLock);
        self->errors.emplace_back(e.what());
        return nullptr;
    } catch (...) {
        std::lock_guard<std::mutex> errLock(self->resultLock);
        self->errors.emplace_back("write task non-standard crash");
        return nullptr;
    }
}

BufferedOutStream *Writer::writeHull(Writer *self, const HullType *ht,
                                     BlockID block) {
    try {
        BufferedOutStream *buffer = nullptr;
        const ObjectID size = ht->IDs.size();
        bool done = true;

        if (const auto t =
              dynamic_cast<const fieldTypes::ContainerType *>(ht)) {
            if (0 != size) {
                bool hasblocks = false;

                // iff we have blockID zero we may need to split
                if (0 == block) {
                    // split non-HS blocks that are too large into blocks
                    if (t->typeID != KnownTypeID::STRING &&
                        size > ogss::HD_Threshold) {
                        hasblocks = true;
                        // we have to fork this task
                        int blockCount = (size - 1) / ogss::HD_Threshold;

                        std::lock_guard<std::mutex> rLock(self->resultLock);

                        // @note we increment await by blockCount - 1
                        self->awaitBuffers += blockCount++;

                        t->blocks = blockCount;
                        for (int i = 1; i < blockCount; i++) {
                            self->results.emplace_back(std::async(
                              std::launch::async, writeHull, self, t, i));
                        }
                    }
                } else {
                    hasblocks = true;
                }

                buffer = new BufferedOutStream();
                buffer->v64(t->fieldID);
                buffer->v64(size);
                if (size > ogss::HD_Threshold) {
                    buffer->v64(block);
                }
                ObjectID i = block * ogss::HD_Threshold;
                const ObjectID end = std::min(size, i + ogss::HD_Threshold);
                t->write(i, end, buffer);

                // close buffer and discard it if possible
                buffer->close();

                if (hasblocks) {
                    done = 0 == --t->blocks;
                }
            }
        } else {
            buffer = new BufferedOutStream();
            bool discard = ((StringPool *)ht)->write(buffer);

            // close buffer and discard it if possible
            buffer->close();
            if (discard) {
                delete buffer;
                buffer = nullptr;
            }
        }

        if (done) {
            if (auto p =
                  dynamic_cast<const fieldTypes::SingleArgumentType *>(ht)) {
                if (auto bt = dynamic_cast<HullType *>(p->base)) {
                    if (0 == --bt->deps) {
                        std::lock_guard<std::mutex> rLock(self->resultLock);
                        self->results.push_back(std::async(
                          std::launch::async, writeHull, self, bt, 0));
                    }
                }
            } else if (dynamic_cast<const StringPool *>(ht)) {
                // nothing to do (in fact we cant type check a MapType)
            } else {
                const auto mt = (fieldTypes::MapType<::ogss::api::Box, ::ogss::api::Box> *)ht;
                if (auto bt = dynamic_cast<HullType *>(mt->keyType)) {
                    if (0 == --bt->deps) {
                        std::lock_guard<std::mutex> rLock(self->resultLock);
                        self->results.push_back(std::async(
                          std::launch::async, writeHull, self, bt, 0));
                    }
                }
                if (auto bt = dynamic_cast<HullType *>(mt->valueType)) {
                    if (0 == --bt->deps) {
                        std::lock_guard<std::mutex> rLock(self->resultLock);
                        self->results.push_back(std::async(
                          std::launch::async, writeHull, self, bt, 0));
                    }
                }
            }
        }

        return buffer;
    } catch (std::exception &e) {
        std::lock_guard<std::mutex> errLock(self->resultLock);
        self->errors.emplace_back(e.what());
        return nullptr;
    } catch (...) {
        std::lock_guard<std::mutex> errLock(self->resultLock);
        self->errors.emplace_back("write task non-standard crash");
        return nullptr;
    }
}
