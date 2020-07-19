//
// Created by Timm Felden on 03.02.16.
//

#ifndef SKILL_CPP_COMMON_OUTSTREAM_H
#define SKILL_CPP_COMMON_OUTSTREAM_H

#include <cstdint>
#include <cstddef>
#include <functional>

namespace ogss {
    namespace streams {

        /**
         * Abstract skill stream.
         *
         * @author Timm Felden
         */
        class Stream {

        protected:

            //! size of the backing buffer
            static constexpr size_t BUFFER_SIZE = 4096;

            /**
             * base pointer of the stream.
             * We keep the base pointer, because it is required for unmap and sane error reporting.
             */
            void *const base;

            /**
             * position inside of the stream
             */
            uint8_t *position;
            /**
             * end pointer of the stream. The stream is done, if position reached end.
             */
            void *const end;

            Stream(void *base, void *end) : base(base), position((uint8_t*)base), end(end) { }

        public:

            /**
             * Proper destruction happens in child destructors
             */
            virtual ~Stream() { }

            /**
             * the position of this stream within its bounds
             */
            size_t getPosition() const noexcept {
                return (size_t) position - (size_t) base;
            }

            inline bool eof() const noexcept {
                return std::greater_equal<void*>()(position, end);
            }

            inline bool has(size_t amountLeft) const noexcept {
                return std::less<void*>()(position + amountLeft, end);
            }
        };
    }
}

#endif //SKILL_CPP_COMMON_OUTSTREAM_H
