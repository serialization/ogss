//
// Created by Timm Felden on 06.11.15.
//

#ifndef SKILL_CPP_COMMON_OBJECT_H
#define SKILL_CPP_COMMON_OBJECT_H

#include "String.h"

namespace ogss {
    namespace internal {
        class FieldDeclaration;

        class DistributedField;

        class LazyField;

        class AbstractPool;

        template<class T>
        class Pool;

        class Writer;
    }
    namespace fieldTypes {
        class AnyRefType;
    }
    namespace api {

        /**
         * an abstract skill object, i.e. the type of an annotation and the base type of
         * every known and unknown skill type instance.
         */
        class Object {

        protected:
            ObjectID id;

            //! bulk allocation
            Object() {};

        public:
            /**
             * @return the static type ID
             * @note types with stid == -1 are named and hold a pointer to their pool
             */
            virtual TypeID stid() const = 0;

            /**
             * query, whether the object is marked for deletion and will be destroyed on flush
             */
            bool isDeleted() const { return 0 == id; }

            /**
             * inserts a human readable presentation of the object into the argument ostream
             *
             * @note constant time operation, i.e. referenced objects are not pretty themselves
             *
             * @todo should not be virtual!
             */
            virtual void prettyString(std::ostream &os) const = 0;

            friend class internal::AbstractPool;

            template<class T>
            friend
            class internal::Pool;

            template<typename T>
            friend
            struct std::hash;

            friend class internal::FieldDeclaration;

            friend class internal::DistributedField;

            friend class internal::LazyField;
            
            friend class internal::Writer;

            friend class fieldTypes::AnyRefType;
        };

        /**
         * An Obj that holds a pointer to its pool.
         *
         * @author Timm Felden
         * @note This type definition is in internal, because we have to protect the user from tampering with ID
         */
        struct NamedObj {
            explicit NamedObj(const internal::AbstractPool *pool) : pool(pool) {}

            const internal::AbstractPool *const pool;
        };
    }
}

inline std::ostream &operator<<(std::ostream &os, const ogss::api::Object &obj) {
    obj.prettyString(os);
    return os;
}

inline std::ostream &operator<<(std::ostream &os, const ogss::api::Object *obj) {
    obj->prettyString(os);
    return os;
}

#endif //SKILL_CPP_COMMON_OBJECT_H
