//
// Created by Timm Felden on 05.04.19.
//

#ifndef OGSS_TEST_CPP_WRITER_H
#define OGSS_TEST_CPP_WRITER_H

#include "../fieldTypes/HullType.h"
#include <future>
#include <mutex>

namespace ogss {
namespace api {
class File;
}

namespace internal {

/**
 * Write a File to disk.
 *
 * @author Timm Felden
 */
class Writer final {
    /// prevent that the buffer consumer interferes with a hull task
    /// @note this should go; we should use a non-blocking queue instead
    std::mutex resultLock;
    std::vector<std::future<BufferedOutStream *>> results;
    std::vector<std::string> errors;

    std::atomic<uint32_t> awaitBuffers;

    Writer(api::File *state, streams::FileOutputStream &out);

    uint32_t writeTF(api::File *state, BufferedOutStream &out);

    static void compress(AbstractPool *base, int *bpos);

    /**
     * writing a field can trigger writing a hull, hence we require access to
     * results
     */
    static BufferedOutStream *writeField(Writer *self, DataField *f,
                                         BlockID block);

    /**
     * writing a hull can trigger more hulls, hence we require access to results
     */
    static BufferedOutStream *
    writeHull(Writer *self, const fieldTypes::HullType *f, BlockID block);

    friend class api::File;
};
} // namespace internal
} // namespace ogss

#endif // OGSS_TEST_CPP_WRITER_H
