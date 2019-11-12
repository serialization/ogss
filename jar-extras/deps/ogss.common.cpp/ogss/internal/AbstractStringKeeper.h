//
// Created by Timm Felden on 05.04.19.
//

#ifndef OGSS_TEST_CPP_ABSTRACTSTRINGKEEPER_H
#define OGSS_TEST_CPP_ABSTRACTSTRINGKEEPER_H

#include "../api/String.h"

namespace ogss {
    namespace internal {
        /**
         * Enforce in-memory order of literal strings.
         *
         * @author Timm Felden
         */
        struct AbstractStringKeeper {
            const ObjectID size;
            /**
             * Begin of the ordered array of string literals.
             */
            const ogss::api::String strings[0];

            AbstractStringKeeper(const ObjectID size) : size(size), strings{} {};
        };
    }
}


#endif //OGSS_TEST_CPP_ABSTRACTSTRINGKEEPER_H
