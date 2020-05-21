//
// Created by Felix Krause on 29.04.20.
//

#ifndef OGSS_TEST_CPP_MARKANDSWEEPBASE_H

#include <bitset>

#include "../api/File.h"
#include "../api/Object.h"
#include "../internal/Pool.h"
#include "../iterators/AllObjectIterator.h"

namespace ogss {
namespace api {

/**
 * General implementation of Mark-and-Sweep on objects of a File.
 * An implementation for the types of a spec can be generated with
 * -Ocpp:markAndSweep=true.
 *
 * During the lifetime of this object, objects in the file may not be created,
 * removed, or modified. The generated implementation cannot handle NamedObjs
 * and will fail if it encounters any.
 */
class AbstractMarkAndSweep {
    api::File *file;
    std::unordered_set<api::Object*> seen;

    std::size_t neededBuckets() {
        // re-hashing is expensive so we determine the total number of objects.
        size_t count = 0;
        for (auto *pool : *file) {
            if (pool->super == nullptr) count += pool->size();
        }
        return count + 1; // accommodate for float rounding errors
    }
  protected:
    explicit AbstractMarkAndSweep(api::File *file):
            file(file), seen(neededBuckets()) {
        seen.max_load_factor(1.0);
    }

    /**
     * an implementation must call mark() on all objects in non-auto fields
     * of the object.
     */
    virtual void markReferenced(api::Object *obj) =0;
  public:
    /**
     * Marks the given object and everything it references.
     */
    void mark(api::Object *obj) {
        if (obj == nullptr || obj->isDeleted() || seen.count(obj) > 0) return;
        seen.insert(obj);
        markReferenced(obj);
    }

    /**
     * Frees all unmarked objects.
     * Caution: Calling this without calling mark() will delete everything!
     */
    void sweep() {
        for (auto *pool : *file) {
            if (pool->super != nullptr) continue;
            auto it = pool->allObjects();
            while (it->hasNext()) {
                auto *obj = it->next();
                if (seen.count(obj) == 0) file->free(obj);
            }
        }
    }
};

} // namespace internal
} // namespace ogss

#endif // OGSS_TEST_CPP_MARKANDSWEEPBASE_H