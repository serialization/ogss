//
// Created by Timm Felden on 06.11.15.
//

#ifndef SKILL_CPP_COMMON_COMMON_H_H
#define SKILL_CPP_COMMON_COMMON_H_H

#include <cstdint>

namespace ogss {
    /**
     * The maximum size of a field data block (2**20)
     */
    const int FD_Threshold = 1048576;

    /**
     * The maximum size of a hull data block (2**14)
     */
    const int HD_Threshold = 16384;

    /**
     * keep the skill id type, and thus the number of treatable skill objects, configurable
     */
    typedef int ObjectID;

    /**
     * keep the number of types configurable and independent of skill ids
     */
    typedef int TypeID;

    /**
     * These typeIDs belong to language defined types and are therefore eternal constants.
     *
     * @note we cannot take an enum class because we need implicit conversion to TypeID
     */
    namespace KnownTypeID {
        enum KnownTypeID : TypeID {
            BOOL = 0,
            I8 = 1,
            I16 = 2,
            I32 = 3,
            I64 = 4,
            V64 = 5,
            F32 = 6,
            F64 = 7,
            ANY_REF = 8,
            STRING = 9
        };
    }

    /**
     * In theory, blockIDs can be up to 2**50 (2**60/2**14).
     * In practice, they are almost always smaller than 100.
     */
    typedef int BlockID;

    /**
     * the base type of generated enum constants
     *
     * @note setting an enum value not corresponding to a proxy has undefined semantics (very likely crash)
     */
    typedef uint32_t EnumBase;

    /**
     * zero cost signed to unsigned conversion
     */
    inline uint64_t unsign(int64_t v) {
        union {
            int64_t s;
            uint64_t u;
        } x;
        static_assert(sizeof(x) == sizeof(int64_t), "assumption on size failed");
        x.s = v;
        return x.u;
    }

    /**
     * zero cost signed to unsigned conversion
     */
    inline uint32_t unsign(int32_t v) {
        union {
            int32_t s;
            uint32_t u;
        } x;
        static_assert(sizeof(x) == sizeof(int32_t), "assumption on size failed");
        x.s = v;
        return x.u;
    }
}

#endif //SKILL_CPP_COMMON_COMMON_H_H
