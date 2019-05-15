//
// Created by Timm Felden on 23.11.15.
//

#ifndef SKILL_CPP_COMMON_BUILTINFIELDTYPE_H
#define SKILL_CPP_COMMON_BUILTINFIELDTYPE_H

#include "FieldType.h"
#include "../utils.h"
#include "../api/types.h"
#include "../streams/BufferedOutStream.h"

namespace ogss {
    using streams::BufferedOutStream;
    using streams::InStream;
    namespace fieldTypes {

        /**
         * generic implementation of builtin types, where <T> is the base type in C++
         */
        template<typename T, TypeID id>
        class BuiltinFieldType : public FieldType {

        protected:
            BuiltinFieldType() : FieldType(id) {}
        };

        /**
         * all field types that have no state whatsoever
         */
        template<typename T, TypeID id,
                api::Box Read(InStream &),
                bool Write(api::Box, streams::BufferedOutStream *)>
        struct StatelessFieldType : BuiltinFieldType<T, id> {
            StatelessFieldType() : BuiltinFieldType<T, id>() {}

            virtual api::Box r(streams::InStream &in) const {
                return Read(in);
            }

            virtual bool w(api::Box v, streams::BufferedOutStream *out) const {
                return Write(v, out);
            }
        };

        struct BoolFieldType : public StatelessFieldType<bool, 0, InStream::boolBox,
                BufferedOutStream::boolBox> {
        };

        extern BoolFieldType BoolType;

        struct I8FieldType : public StatelessFieldType<int8_t, 1, InStream::i8Box,
                BufferedOutStream::i8Box> {
        };

        extern I8FieldType I8;

        struct I16FieldType : public StatelessFieldType<int16_t, 2, InStream::i16Box,
                BufferedOutStream::i16Box> {
        };

        extern I16FieldType I16;

        struct I32FieldType : public StatelessFieldType<int32_t, 3, InStream::i32Box,
                BufferedOutStream::i32Box> {
        };

        extern I32FieldType I32;

        struct I64FieldType : public StatelessFieldType<int64_t, 4, InStream::i64Box,
                BufferedOutStream::i64Box> {
        };

        extern I64FieldType I64;

        struct V64FieldType : public StatelessFieldType<int64_t, 5, InStream::v64Box,
                BufferedOutStream::v64Box> {
        };

        extern V64FieldType V64;

        struct F32FieldType : public StatelessFieldType<float, 6, InStream::i32Box,
                BufferedOutStream::i32Box> {
        };

        extern F32FieldType F32;

        struct F64FieldType : public StatelessFieldType<double, 7, InStream::i64Box,
                BufferedOutStream::i64Box> {
        };

        extern F64FieldType F64;
    }

}


#endif //SKILL_CPP_COMMON_BUILTINFIELDTYPE_H
