//
// Created by Timm Felden on 03.04.19.
//

#include <algorithm>

#include "SeqParser.h"
#include "../concurrent/Pool.h"
#include "../fieldTypes/ContainerType.h"
#include "LazyField.h"

using namespace ogss::internal;
using ogss::concurrent::Job;

namespace ogss {
namespace internal {

class SeqReadTask final : public Job {
    const BlockID block;

    DataField *const f;

    streams::MappedInStream *const in;

  public:
    SeqReadTask(DataField *f, BlockID block, streams::MappedInStream *in) :
      block(block),
      f(f),
      in(in) {}

    ~SeqReadTask() final {
        if (!dynamic_cast<LazyField *>(f))
            delete in;
    }

    void run() final {
        AbstractPool *const owner = f->owner;
        const int bpo = owner->bpo;
        const int first = block * ogss::FD_Threshold;
        const int last =
          std::min(owner->cachedSize, first + ogss::FD_Threshold);

        f->read(bpo + first, bpo + last, *in);

        if (!in->eof() && !(dynamic_cast<LazyField *>(f)))
            throw std::out_of_range("read task did not consume InStream");
    }
};

class SHRT final : public Job {
    const BlockID block;
    fieldTypes::ContainerType *const t;
    streams::MappedInStream *const in;

  public:
    SHRT(fieldTypes::ContainerType *t, int block, streams::MappedInStream *in) :
      block(block),
      t(t),
      in(in) {}

    ~SHRT() final { delete in; }

    void run() override {
        ObjectID i = block * ogss::HD_Threshold;
        const ObjectID end =
          std::min((ObjectID)t->idMap.size() - 1, i + ogss::HD_Threshold);

        t->read(i, end, in);
    }
};
} // namespace internal
} // namespace ogss

SeqParser::SeqParser(const std::string &path, streams::FileInputStream *in,
                     const PoolBuilder &pb) :
  Parser(path, in, pb) {}

void SeqParser::typeBlock() {
    /**
     * *************** * T Class * ****************
     */
    typeDefinitions();

    // calculate cached size and next for all pools
    {
        int cs = classes.size();
        if (0 != cs) {
            int i = cs - 2;
            if (i >= 0) {
                AbstractPool *n, *p = classes[i + 1];
                // propagate information in reverse order
                // i is the pool where next is set, hence we skip the last pool
                do {
                    n = p;
                    p = classes[i];

                    // by compactness, if n has a super pool, p is the previous
                    // pool
                    if (n->super) {
                        n->super->cachedSize += n->cachedSize;
                    }

                } while (--i >= 0);
            }

            // allocate data and start instance allocation jobs
            // note: this is different from Java, because we used templates in
            // C++
            while (++i < cs) {
                AbstractPool *p = classes[i];
                p->allocateData();
                p->lastID = p->bpo + p->cachedSize;
                if (0 != p->staticDataInstances) {
                    p->allocateInstances();
                }
            }
        }
    }

    /**
     * *************** * T Container * ****************
     */
    TContainer();

    /**
     * *************** * T Enum * ****************
     */
    TEnum();

    /**
     * *************** * F * ****************
     */
    for (AbstractPool *p : classes) {
        readFields(p);
    }
}

void SeqParser::processData() {
    // we expect one HD-entry per field, but an arbitrary amount of entries can
    // be encountered
    std::vector<Job *> jobs;
    jobs.reserve(fields.size());

    while (!in->eof()) {
        // create the in directly and use it for subsequent read-operations to
        // avoid costly position and size readjustments
        streams::MappedInStream *const map = in->jumpAndMap(in->v32() + 2);

        const int id = map->v32();
        RTTIBase *const f = fields.at(id);

        // TODO add a countermeasure against duplicate buckets / fieldIDs

        if (auto p = dynamic_cast<HullType *>(f)) {
            const int count = map->v32();

            // start hull allocation job
            BlockID block = p->allocateInstances(count, map);

            // create hull read data task except for StringPool which is still
            // lazy per element and eager per offset
            if (auto ct = dynamic_cast<fieldTypes::ContainerType *>(p)) {
                jobs.push_back(new SHRT(ct, block, map));
            }

        } else if (auto fd = dynamic_cast<DataField *>(f)) {
            BlockID block =
              fd->owner->cachedSize > ogss::FD_Threshold ? map->v32() : 0;

            // create job with adjusted size that corresponds to the * in the
            // specification (i.e. exactly the data)
            jobs.push_back(new SeqReadTask(fd, block, map));
        } else {
            delete map;
            ParseException(in.get(), "corrupted HD block");
        }
    }

    // perform read tasks
    int i = 0;
    try {
        for (; i < jobs.size(); i++) {
            Job *j = jobs[i];
            j->run();
            delete j;

            // TODO default initialization!
        }
    } catch (std::exception &e) {
        // delete remaining jobs in case of error
        for (; i < jobs.size(); i++) {
            delete jobs[i];
        }

        throw e;
    }
}
