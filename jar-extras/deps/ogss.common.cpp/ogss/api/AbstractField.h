//
// Created by Timm Felden on 20.11.15.
//

#ifndef SKILL_CPP_COMMON_API_FIELDDECLARATION_H
#define SKILL_CPP_COMMON_API_FIELDDECLARATION_H

#include "String.h"
#include "Box.h"
#include "../fieldTypes/FieldType.h"
#include "../internal/RTTIBase.h"

namespace ogss {
    using fieldTypes::FieldType;
    namespace api {
        /**
         * Public part of Skill field RTTI.
         *
         * @author Timm Felden
         */
        class AbstractField : public internal::RTTIBase {
        protected:
            AbstractField(const FieldType *const type, String const name)
                    : type(type), name(name) {}

        public:

            const FieldType *const type;
            const String name;

            /**
             * reflective get
             * @note it is silently assumed, that owner.contains(i)
             * @note known fields provide .get methods that are generally faster, because
             * they do not require boxing
             */
            virtual Box getR(const Object *i) = 0;

            /**
             * reflective set
             * @note it is silently assumed, that owner.contains(i)
             * @note known fields provide .set methods that are generally faster, because
             * they do not require boxing
             */
            virtual void setR(Object *i, Box v) = 0;
        };
    }
}


#endif //SKILL_CPP_COMMON_FIELDDECLARATION_H
