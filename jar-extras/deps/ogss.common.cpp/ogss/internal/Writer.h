//
// Created by Timm Felden on 05.04.19.
//

#ifndef OGSS_TEST_CPP_WRITER_H
#define OGSS_TEST_CPP_WRITER_H

#include <future>
#include <mutex>
#include "../fieldTypes/HullType.h"

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

            Writer(api::File *state, streams::FileOutputStream &out);

            uint32_t writeTF(api::File *state, BufferedOutStream &out);

            static void compress(AbstractPool *base, int *bpos);

            /**
             * writing a field can trigger writing a hull, hence we require access to results
             */
            static BufferedOutStream *writeField(Writer *self, DataField *f);

            /**
             * writing a hull can trigger more hulls, hence we require access to results
             */
            static BufferedOutStream *writeHull(Writer *self, fieldTypes::HullType *f);

            friend class api::File;
        };
    }
}

#endif //OGSS_TEST_CPP_WRITER_H
