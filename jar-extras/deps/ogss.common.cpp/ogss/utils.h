//
// Created by feldentm on 03.11.15.
//

#ifndef OGSS_CPP_COMMON_UTILS_H
#define OGSS_CPP_COMMON_UTILS_H

#include <string>
#include <stdexcept>

/**
 * Wrap the temporary message on a stack-object, that is immediately deconstructed to allow adding to the message while
 * keeping file and line makros usable.
 *
 * @author Timm Felden
 */
struct OGSS_TODO_MESSAGE final {
    std::string msg;

    explicit OGSS_TODO_MESSAGE(const std::string &msg) : msg(msg) {}

    ~OGSS_TODO_MESSAGE() noexcept(false) {
        throw std::logic_error(msg);
    }

    OGSS_TODO_MESSAGE &operator+(const std::string &str) {
        msg += str;
        return *this;
    }

};


#define SK_TODO OGSS_TODO_MESSAGE(std::string("TODO -- not yet implemented: ") + __FILE__ + ":" + std::to_string(__LINE__) + "\n")


#endif //OGSS_CPP_COMMON_UTILS_H
