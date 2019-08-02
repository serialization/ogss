//
// Created by Timm Felden on 15.04.19.
//

#ifndef OGSS_COMMON_API_ENUM_H
#define OGSS_COMMON_API_ENUM_H

#include "../common.h"

namespace ogss {
    namespace internal {
        class AbstractEnumPool;

        template<typename T>
        class EnumPool;
    }
    namespace api {

        /**
         * The generic base type of enum proxies.
         * @author Timm Felden
         */
        class AbstractEnumProxy {
        public:

            /**
             * The untyped enum constant.
             * @note -1 for all unknown enum values.
             */
            const EnumBase constant;

            /**
             * The pool holding this proxy.
             */
            internal::AbstractEnumPool *const owner;

            /**
             * The name of the value of this constant.
             * @note the value is set even for unknown values.
             */
            const String name;

            /**
             * The id of this value as of the combined type system.
             */
            const ObjectID id;

        protected:
            /// prevent instantiation of abstract class
            AbstractEnumProxy(EnumBase constant, internal::AbstractEnumPool *owner, String name, ObjectID id)
                    : constant(constant), owner(owner), name(name), id(id) {}
        };

        /**
         * An enum with no known value, i.e. one that is not part of the tool specification.
         */
        enum UnknownEnum : EnumBase {
            UNKNOWN = (EnumBase) - 1
        };

        template<typename T>
        class EnumProxy : public AbstractEnumProxy {
            EnumProxy(T constant, internal::EnumPool<T> *owner, String name, ObjectID id)
                    : AbstractEnumProxy((EnumBase) constant, owner, name, id) {}

            friend class internal::EnumPool<T>;

        public:
            inline T value() const {
                // add null check to increase robustness slightly
                return (T) (this ? this->constant : 0);
            }
        };

    }
}

#endif //OGSS_COMMON_API_ENUM_H
