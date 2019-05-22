//
// Created by Timm Felden on 04.11.15.
//

#ifndef SKILL_CPP_COMMON_MAPPEDINSTREAM_H
#define SKILL_CPP_COMMON_MAPPEDINSTREAM_H


#include "InStream.h"

namespace ogss {
    namespace streams {
        class FileInputStream;

        /**
         * Name chosen for historical reasons. In fact, all streams share the same map.
         */
        class MappedInStream : public InStream {
        private:
            friend class FileInputStream;

            //! only file input streams can create mapped streams
            MappedInStream(void *base, uint8_t *position, void *end);

        public:
            // copy a mapped in stream for later use
            explicit MappedInStream(const MappedInStream *other)
                    : InStream(other->base, other->end) {
                this->position = other->position;
            }

            //! create a view of another mapped stream
            MappedInStream(const MappedInStream *other, size_t begin, size_t end);

            //! requires no action; all resources are managed elsewhere
            ~MappedInStream() override = default;
        };
    }
}


#endif //SKILL_CPP_COMMON_MAPPEDINSTREAM_H
