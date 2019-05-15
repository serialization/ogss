//
// Created by Timm Felden on 20.11.15.
//

#ifndef SKILL_CPP_COMMON_LAZYFIELD_H
#define SKILL_CPP_COMMON_LAZYFIELD_H

#include <map>
#include "DistributedField.h"

namespace ogss {
    namespace internal {
        class LazyField : public DistributedField {

            streams::MappedInStream *input;

            inline bool isLoaded() { return nullptr == input; }

            void load();

        public:
            LazyField(FieldType *const type, api::String name,
                      TypeID index, AbstractPool *const owner)
                    : DistributedField(type, name, index, owner),
                      input(nullptr) {}

            virtual ~LazyField();

//            virtual void resetChunks(ObjectID lbpo, ObjectID newSize) {
//                ensureIsLoaded();
//                DistributedField::resetChunks(lbpo, newSize);
//            }

            inline void ensureIsLoaded() {
                if (!isLoaded())
                    load();
            }

            virtual api::Box getR(const api::Object *i) override;

            virtual void setR(api::Object *i, api::Box v) override;

            virtual bool check() const override;
        };
    }
}


#endif //SKILL_CPP_COMMON_LAZYFIELD_H
