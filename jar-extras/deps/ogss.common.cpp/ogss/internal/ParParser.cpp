//
// Created by Timm Felden on 16.05.19.
//

#include "ParParser.h"
#include "../fieldTypes/ContainerType.h"
#include "LazyField.h"

#include <future>

using namespace ogss::internal;

using ogss::concurrent::Job;
using ogss::concurrent::Semaphore;

namespace ogss {
namespace internal {

// TODO error reporting in tasks will currently not work as intended!

class ParReadTask final : public Job {

    const BlockID block;
    DataField *const f;
    streams::MappedInStream *const in;
    Semaphore *const barrier;

  public:
    ParReadTask(DataField *f, BlockID block, streams::MappedInStream *in,
                Semaphore *barrier) :
      block(block),
      f(f),
      in(in),
      barrier(barrier) {}

    ~ParReadTask() final {
        if (!dynamic_cast<LazyField *>(f))
            delete in;
    }

    void run() final {
        Semaphore::ScopedPermit release(barrier);

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

class PHRT final : public Job {

    const BlockID block;
    fieldTypes::ContainerType *const t;
    streams::MappedInStream *const in;
    Semaphore *const barrier;

  public:
    PHRT(fieldTypes::ContainerType *t, int block, streams::MappedInStream *in,
         Semaphore *barrier) :
      block(block),
      t(t),
      in(in),
      barrier(barrier) {}

    ~PHRT() final { delete in; }

    void run() override {
        Semaphore::ScopedPermit release(barrier);

        ObjectID i = block * ogss::HD_Threshold;
        const ObjectID end =
          std::min((ObjectID)t->idMap.size() - 1, i + ogss::HD_Threshold);
        t->read(i, end, in);
    }
};
} // namespace internal
} // namespace ogss

ParParser::ParParser(const std::string &path, streams::FileInputStream *in,
                     const PoolBuilder &pb) :
  Parser(path, in, pb),
  barrier(),
  jobs(),
  jobMX() {
    // we use a thread pool, so we have to create it
    threadPool = new concurrent::Pool();
}

ParParser::~ParParser() noexcept(false) {
    barrier.takeMany(jobs.size());

    if (threadPool->hasErrors()) {
        // error propagation code, i.e. aggregate error messages
        std::vector<std::string> errors;
        threadPool->takeErrors(errors);
        std::stringstream ss;
        ss << "read jobs had errors:" << std::endl;
        for (auto &e : errors) {
            ss << "  " << e << std::endl;
        }
        throw ogss::Exception(ss.str());
    }
}

/**
 * parse T and F
 */
void ParParser::typeBlock() {

    /**
     * *************** * T Class * ****************
     */
    typeDefinitions();

    // calculate cached size and next for all pools
    {
        const int cs = classes.size();
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
            while (++i < cs) {
                AbstractPool *p = classes[i];
                p->allocateData();
                p->lastID = p->bpo + p->cachedSize;
                if (0 != p->staticDataInstances) {
                    threadPool->run(new AllocateInstances(p, &barrier));
                } else {
                    // we would not allocate an instance anyway
                    barrier.release();
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

/**
 * Jump through HD-entries to create read tasks
 */
void ParParser::processData() {

    // we expect one HD-entry per field
    jobs.reserve(fields.size());

    int awaitHulls = 0;

    while (!in->eof()) {
        // create the map directly and use it for subsequent read-operations to
        // avoid costly position and size readjustments
        streams::MappedInStream *const map = in->jumpAndMap(in->v32() + 2);

        const int id = map->v32();
        RTTIBase *const f = fields.at(id);

        // TODO add a countermeasure against duplicate buckets / fieldIDs

        if (auto p = dynamic_cast<HullType *>(f)) {
            const int count = map->v32();

            // start hull allocation job
            awaitHulls++;
            threadPool->run(new AllocateHull(p, count, map, this));

        } else if (auto fd = dynamic_cast<DataField *>(f)) {
            BlockID block =
              fd->owner->cachedSize > ogss::FD_Threshold ? map->v32() : 0;

            // create job with adjusted size that corresponds to the * in the
            // specification (i.e. exactly the data)
            std::lock_guard<std::mutex> lock(jobMX);
            jobs.push_back(new ParReadTask(fd, block, map, &barrier));
        }
    }

    // await allocations of class and hull types
    barrier.takeMany(classes.size() + awaitHulls);

    // start read tasks
    threadPool->runAll(jobs);

    // TODO start tasks that perform default initialization of fields not
    // obtained from file
}

void ParParser::AllocateHull::run() {
    concurrent::Semaphore::ScopedPermit release(&self->barrier);
    int block = p->allocateInstances(count, map);

    // create hull read data task except for StringPool which is still lazy per
    // element and eager per offset
    if (const auto ct = dynamic_cast<fieldTypes::ContainerType *>(p)) {
        std::lock_guard<std::mutex> lock(self->jobMX);
        self->jobs.push_back(new PHRT(ct, block, map, &self->barrier));
    }
}
