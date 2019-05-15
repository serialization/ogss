//
// Created by Timm Felden on 03.04.19.
//

#ifndef OGSS_TEST_CPP_SEQPARSER_H
#define OGSS_TEST_CPP_SEQPARSER_H

#include "Parser.h"

namespace ogss {
    namespace internal {

        /**
         * A sequential .sg-file parser.
         *
         * @author Timm Felden
         */
        class SeqParser final : public Parser {
            SeqParser(const std::string &path, streams::FileInputStream *in, const PoolBuilder &pb);

            ~SeqParser() final = default;

            void typeBlock() final;

            void processData() final;

            friend struct StateInitializer;
        };
    }
}


#endif //OGSS_TEST_CPP_SEQPARSER_H
