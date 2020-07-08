//
// Created by Timm Felden on 25.03.19.
//

#ifndef SKILL_CPP_COMMON_BUFFEREDOUTSTREAM_H
#define SKILL_CPP_COMMON_BUFFEREDOUTSTREAM_H

#include "../api/Box.h"
#include "FileOutputStream.h"
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>
#include <stdexcept>

namespace ogss {
namespace streams {

/**
 * Buffered output streams.
 *
 * @note in contrast to OGSS/JVM, buffers will not be recycled because C++
 * stdlib lacks the required data structures
 * @note in contrast to OGSS/JVM, we can directly write bools to the last byte,
 * what makes BoolWrapper pointless
 */
class BufferedOutStream {
    friend class FileOutputStream;

    //! number of bytes written to the file; used for truncation on close
    size_t bytesWriten;

    struct Buffer {
        //! the current backing buffer
        uint8_t *begin;
        //! the end of the current backing buffer
        const uint8_t *const end;

        //! used to restore begin; if negative, the buffer is wrapped, i.e.
        //! begin must not be freed
        const int size;
    };

    // @note inv: current.size is == BUFFER_SIZE
    Buffer current;

    /**
     * Offset of last boolean operation. We start with a full byte, because the
     * first last byte has to be the absolute first byte.
     *
     * @note this implementation is sane because a buffer containing bool cannot
     * contain any other entity.
     */
    uint8_t off = 7;

    /**
     * completed buffers
     */
    std::vector<Buffer> completed;

    /**
     * flush the buffer
     */
    void flush();

    inline void require(int i) {
        if ((int)(current.end - current.begin) < i)
            flush();
    }

  public:
    BufferedOutStream() :
      bytesWriten(0),
      current({nullptr, nullptr, 0}),
      off(7),
      completed() {}

    /**
     * Release buffered memory owned by this stream
     */
    ~BufferedOutStream() {
        for (auto &b : completed)
            if (b.size > 0)
                free(b.begin);
    }

    static inline bool boolBox(api::Box v, streams::BufferedOutStream *out) {
        out->boolean(v.boolean);
        return !v.boolean;
    }

    inline void boolean(bool v) {
        if (8 == ++off) {
            off = 0;
            if (current.end == current.begin) {
                flush();
            }
            *(current.begin++) = 0;
        }

        if (v) {
            *(current.begin - 1) |= (1u << off);
        }
    }

    static inline bool i8Box(api::Box v, streams::BufferedOutStream *out) {
        out->i8(v.i8);
        return 0 == v.i8;
    }

    inline void i8(int8_t v) {
        require(1);
        *(current.begin++) = (uint8_t)v;
    }

    static inline bool i16Box(api::Box v, streams::BufferedOutStream *out) {
        out->i16(v.i16);
        return 0 == v.i16;
    }

    inline void i16(int16_t v) {
        require(2);
        std::memcpy(current.begin, &v, 2);
        current.begin += 2;
    }

    static inline bool i32Box(api::Box v, streams::BufferedOutStream *out) {
        out->i32(v.i32);
        return 0 == v.i32;
    }

    inline void i32(int32_t v) {
        require(4);
        std::memcpy(current.begin, &v, 4);
        current.begin += 4;
    }

    inline void f32(float v) {
        require(4);
        std::memcpy(current.begin, &v, 4);
        current.begin += 4;
    }

    static inline bool i64Box(api::Box v, streams::BufferedOutStream *out) {
        out->i64(v.i64);
        return 0 == v.i64;
    }

    inline void i64(int64_t v) {
        require(8);
        std::memcpy(current.begin, &v, 8);
        current.begin += 8;
    }

    inline void f64(double v) {
        require(8);
        std::memcpy(current.begin, &v, 8);
        current.begin += 8;
    }

    static inline bool v64Box(api::Box v, streams::BufferedOutStream *out) {
        if (v.i64) {
            out->v64(v.i64);
            return false;
        } else {
            out->i8(0);
            return true;
        }
    }

    inline void v64(int32_t p) {
        if (p < 0)
            throw std::logic_error("value not v32");

        auto v = ::ogss::unsign(p);
        require(5);

        if (v < 0x80U) {
            *(current.begin++) = (uint8_t)(v);
        } else {
            *(current.begin++) = (uint8_t)((0x80U | v));
            if (v < 0x4000u) {
                *(current.begin++) = (uint8_t)((v >> 7u));
            } else {
                *(current.begin++) = (uint8_t)((0x80U | v >> 7u));
                if (v < 0x200000u) {
                    *(current.begin++) = (uint8_t)((v >> 14u));
                } else {
                    *(current.begin++) = (uint8_t)((0x80U | v >> 14u));
                    if (v < 0x10000000u) {
                        *(current.begin++) = (uint8_t)((v >> 21u));
                    } else {
                        *(current.begin++) = (uint8_t)((0x80U | v >> 21u));
                        *(current.begin++) = (uint8_t)((v >> 28u));
                    }
                }
            }
        }
    }

    inline void v64(int64_t p) {
        auto v = ::ogss::unsign(p);
        require(9);

        if (v < 0x80U) {
            *(current.begin++) = (uint8_t)(v);
        } else {
            *(current.begin++) = (uint8_t)((0x80U | v));
            if (v < 0x4000U) {
                *(current.begin++) = (uint8_t)((v >> 7u));
            } else {
                *(current.begin++) = (uint8_t)((0x80U | v >> 7u));
                if (v < 0x200000U) {
                    *(current.begin++) = (uint8_t)((v >> 14u));
                } else {
                    *(current.begin++) = (uint8_t)((0x80U | v >> 14u));
                    if (v < 0x10000000U) {
                        *(current.begin++) = (uint8_t)((v >> 21u));
                    } else {
                        *(current.begin++) = (uint8_t)((0x80U | v >> 21u));
                        if (v < 0x800000000U) {
                            *(current.begin++) = (uint8_t)((v >> 28u));
                        } else {
                            *(current.begin++) = (uint8_t)((0x80U | v >> 28u));
                            if (v < 0x40000000000U) {
                                *(current.begin++) = (uint8_t)((v >> 35u));
                            } else {
                                *(current.begin++) =
                                  (uint8_t)((0x80U | v >> 35u));
                                if (v < 0x2000000000000U) {
                                    *(current.begin++) = (uint8_t)((v >> 42u));
                                } else {
                                    *(current.begin++) =
                                      (uint8_t)((0x80U | v >> 42u));
                                    if (v < 0x100000000000000U) {
                                        *(current.begin++) =
                                          (uint8_t)((v >> 49u));
                                    } else {
                                        *(current.begin++) =
                                          (uint8_t)((0x80U | v >> 49u));
                                        *(current.begin++) =
                                          (uint8_t)((v >> 56u));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Put an array of bytes into the stream. Intended to be used for string
     * images.
     *
     * @note you may not modify data after putting it to a stream, because the
     * actual put might be a deferred operation
     * @note you may not free data before the flush operation has completed
     * @param data the data to be written
     */
    void put(const uint8_t *data, const size_t size) {
        // write the byte[] directly, if it is too large to be cached
        // efficiently
        if (size > FileOutputStream::BUFFER_SIZE / 2) {
            if (current.end - current.begin != FileOutputStream::BUFFER_SIZE)
                flush();
            int wrapSize = -(long)size;
            Buffer wrap = {const_cast<uint8_t *>(data), data + size, wrapSize};
            completed.push_back(wrap);
            bytesWriten += size;

        } else {
            if (current.end - current.begin < (long)size)
                flush();
            std::memcpy(current.begin, data, size);
            current.begin += size;
        }
    }

    /**
     * Ensure that current is flushed to completed and no dead memory would be
     * leaked.
     */
    void close() {
        if (current.begin) {
            int p =
              FileOutputStream::BUFFER_SIZE - (current.end - current.begin);
            if (p) {
                completed.emplace_back(
                  Buffer({current.begin - p, current.end, p}));
                bytesWriten += p;
            } else {
                free(current.begin);
            }
        }
    }
};
} // namespace streams
} // namespace ogss

#endif // SKILL_CPP_COMMON_BUFFEREDOUTSTREAM_H
