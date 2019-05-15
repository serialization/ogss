//
// Created by Timm Felden on 03.11.15.
//

#ifndef SKILL_CPP_COMMON_SKILLEXCEPTION_H
#define SKILL_CPP_COMMON_SKILLEXCEPTION_H

#include <string>
#include <sstream>

namespace ogss {

    /**
     * top level class of everything, that is thrown by SKilL implementations
     */
    class Exception : public std::runtime_error {
    public:

        Exception(const std::string &message) : std::runtime_error(message) {}
    };

}
#endif //SKILL_CPP_COMMON_SKILLEXCEPTION_H
