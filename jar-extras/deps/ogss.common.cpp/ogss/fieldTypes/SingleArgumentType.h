//
// Created by Timm Felden on 01.04.19.
//

#ifndef OGSS_TEST_CPP_SINGLEARGUMENTTYPE_H
#define OGSS_TEST_CPP_SINGLEARGUMENTTYPE_H

#include "HullType.h"

namespace ogss {
    namespace fieldTypes {
        /**
         * A generic type with one type parameter.
         */
        class SingleArgumentType : public HullType {

        protected:
            /**
             * Hull-Reads cache in stream between allocate and read.
             */
            streams::MappedInStream *in;

            SingleArgumentType(TypeID tid, uint32_t kcc, FieldType *const base)
                    : HullType(tid, kcc), in(nullptr), base(base) {};

            ~SingleArgumentType() override = default;

        public:
            FieldType *const base;

            api::Box get(ObjectID ID) const final {
                return api::box(((0 < ID) & (ID < idMap.size()))
                                ? idMap[ID]
                                : nullptr);
            }
        };
    }
}


#endif //OGSS_TEST_CPP_SINGLEARGUMENTTYPE_H
