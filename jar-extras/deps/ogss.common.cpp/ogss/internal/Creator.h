//
// Created by Timm Felden on 29.03.19.
//

#ifndef OGSS_TEST_CPP_CREATOR_H
#define OGSS_TEST_CPP_CREATOR_H

#include "StateInitializer.h"

namespace ogss {
    namespace internal {

        /**
         * Create an empty state. The approach here is different from the generated initialization code in OGSS to
         * reduce the amount of generated code.
         *
         * @author Timm Felden
         */
        struct Creator final : public StateInitializer {
            explicit Creator(const std::string &path, const PoolBuilder &pb);

            ~Creator() final = default;
        };
    }
}


#endif //OGSS_TEST_CPP_CREATOR_H
