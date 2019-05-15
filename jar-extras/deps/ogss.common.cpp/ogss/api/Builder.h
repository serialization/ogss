//
// Created by Timm Felden on 02.04.19.
//

#ifndef OGSS_TEST_CPP_BUILDER_H
#define OGSS_TEST_CPP_BUILDER_H

namespace ogss {
    namespace internal {
        template<class T>
        class SubPool;
    }
    namespace api {
        class Object;

        /**
         * The best way to construct objects in C++, because there is no by-name parameter passing.
         *
         * @note the constructed instance is made available immediately after construction of the builder.
         */
        class Builder {
        protected:
            Object *const self;

            explicit Builder(Object *const instance) : self(instance) {}

            virtual ~Builder() = default;

            template<class T>
            friend
            class internal::SubPool;
        };
    }
}


#endif //OGSS_TEST_CPP_BUILDER_H
