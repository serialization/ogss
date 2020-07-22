//
// Created by feldentm on 22.07.20.
//

#ifndef OGSS_COMMON_CPP_ITERATORPROXY_H
#define OGSS_COMMON_CPP_ITERATORPROXY_H

/**
 * An iterator that exports two member functions as new iterator. This is a nice
 * feature, if multiple begin and end functions are exported by the same class.
 *
 * @tparam T The type exporting begin and end
 * @tparam R The result type of begin and end
 * @tparam _begin T::begin
 * @tparam _end T::end
 * @author Timm Felden
 */
template <class T, typename R, R (T::*FIRST)() const, R (T::*LAST)() const>
class IteratorProxy {
    const T &self;

  public:
    IteratorProxy(const T &self) : self(self) {}

    R begin() const { return (self.*FIRST)(); }

    R end() const { return (self.*LAST)(); }
};

#endif // OGSS_COMMON_CPP_ITERATORPROXY_H
