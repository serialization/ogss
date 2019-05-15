//
// Created by Timm Felden on 05.04.19.
//

#ifndef OGSS_TEST_CPP_RTTIBASE_H
#define OGSS_TEST_CPP_RTTIBASE_H
namespace ogss {
    namespace internal {
        /**
         * Base class of FieldType and FieldDeclaration, to fix dynamic_cast.
         *
         * @author Timm Felden
         */
        struct RTTIBase {
            virtual ~RTTIBase() = default;

        protected:
            RTTIBase() = default;
        };
    }
}
#endif //OGSS_TEST_CPP_RTTIBASE_H
