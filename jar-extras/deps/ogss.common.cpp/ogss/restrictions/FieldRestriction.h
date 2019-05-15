//
// Created by Timm Felden on 24.11.15.
//

#ifndef SKILL_CPP_COMMON_FIELDRESTRICTION_H
#define SKILL_CPP_COMMON_FIELDRESTRICTION_H

#include <assert.h>
#include "../api/Object.h"
#include "../api/Box.h"

namespace ogss {
    namespace restrictions {
        struct FieldRestriction {
            const int id;

            virtual ~FieldRestriction();

        protected:
            FieldRestriction(int id) : id(id) {};
        };

        using api::Box;

        struct CheckableRestriction : public FieldRestriction {

            /**
             * checks argument v.
             * @return true, iff argument fulfills the restriction
             */
            virtual bool check(Box v) const = 0;

        protected:
            CheckableRestriction(int id) : FieldRestriction(id) {};
        };

        struct NonNull : public CheckableRestriction {
            static const NonNull *get();

            virtual bool check(Box v) const {
                return nullptr != v.anyRef;
            }

        private:
            const static NonNull instance;

            NonNull() : CheckableRestriction(0) {}
        };

        template<typename T>
        struct FieldDefault : public FieldRestriction {
            const T value;

            FieldDefault(T v) : FieldRestriction(1), value(v) {};
        };

        template<typename T>
        struct Range : public CheckableRestriction {
            const T min;
            const T max;

            /**
             * construct from inclusive ranges
             */
            Range(T min, T max) : CheckableRestriction(3), min(min), max(max) {
                assert(min <= max);
            };

            virtual bool check(Box v) const {
                const T x = api::unbox<T>(v);
                return min <= x && x <= max;
            }
        };

        struct Coding : public FieldRestriction {
            const api::String coding;

            Coding(api::String coding) : FieldRestriction(5), coding(coding) {}
        };

        struct ConstantLengthPointer : public FieldRestriction {
            static const ConstantLengthPointer *get();

        private:
            const static ConstantLengthPointer instance;

            ConstantLengthPointer() : FieldRestriction(7) {}
        };

    }
}


#endif //SKILL_CPP_COMMON_FIELDRESTRICTION_H
