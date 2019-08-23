//
// Created by Timm Felden on 28.12.15.
//

#ifndef SKILL_CPP_COMMON_DYNAMIC_DATA_ITERATOR_H
#define SKILL_CPP_COMMON_DYNAMIC_DATA_ITERATOR_H

#include <iterator>

namespace ogss {
namespace internal {
template <class T> class Pool;
}
using internal::Pool;

namespace iterators {
/**
 * Iterates efficiently over dynamic instances of a pool.
 *
 * First phase will iterate over all blocks of the pool.
 * The second phase will iterate over all dynamic instances of the pool.
 *
 * @author Timm Felden
 */
template <class T>
class DynamicDataIterator : public std::iterator<std::input_iterator_tag, T> {

    const AbstractPool *p;

    const int endHeight;
    ObjectID index;
    ObjectID last;
    // true if in second phase
    bool second;

    void nextP() {
        AbstractPool *n = p->next;
        if (n && endHeight < n->THH)
            p = n;
        else
            p = nullptr;
    }

  public:
    //! creates an empty iterator
    DynamicDataIterator() :
      p(nullptr),
      endHeight(0),
      index(0),
      last(0),
      second(false) {}

    explicit DynamicDataIterator(const Pool<T> *p) :
      p(p),
      endHeight(p->THH),
      index(p->bpo),
      last(index + p->cachedSize),
      second(false) {

        // mode switch, if no values obtained from data
        if ((index == last) | (nullptr == p->data)) {
            second = true;
            while (this->p) {
                if ((last = ((Pool<T> *)this->p)->newObjects.size())) {
                    index = 0;
                    break;
                }
                nextP();
            }
        }
    }

    DynamicDataIterator(const DynamicDataIterator<T> &iter) :
      p(iter.p),
      endHeight(iter.endHeight),
      index(iter.index),
      last(iter.last),
      second(iter.second) {}

    DynamicDataIterator &operator++() {
        if (++index == last) {
            if (second) {
                nextP();
            } else {
                second = true;
            }
            while (this->p) {
                if ((last = ((Pool<T> *)this->p)->newObjects.size())) {
                    index = 0;
                    break;
                }
                nextP();
            }
        }
        return *this;
    }

    const DynamicDataIterator operator++(int) {
        DynamicDataIterator tmp(*this);
        operator++();
        return tmp;
    }

    //! move to next position and return current element
    T *next() {
        auto r = second ? ((Pool<T> *)p)->newObjects[index]
                        : ((Pool<T> *)p)->data[index];
        this->operator++();
        return r;
    }

    //! @return true, iff another element can be returned
    bool hasNext() const { return p; }

    //! @note all empty iterators are considered equal
    bool operator==(const DynamicDataIterator<T> &iter) const {
        return (!hasNext() && !iter.hasNext()) ||
               (p == iter.p && endHeight == iter.endHeight &&
                second == iter.second && index == iter.index &&
                last == iter.last);
    }

    //! @note all empty iterators are considered equal
    bool operator!=(const DynamicDataIterator<T> &iter) const {
        return (hasNext() || iter.hasNext()) &&
               !(p == iter.p && endHeight == iter.endHeight &&
                 second == iter.second && index == iter.index &&
                 last == iter.last);
    }

    T &operator*() const {
        // @note increment happens before access, because we shifted data by 1
        return *(second ? ((Pool<T> *)p)->newObjects[index]
                        : ((Pool<T> *)p)->data[index]);
    }

    T *operator->() const {
        return second ? ((Pool<T> *)p)->newObjects[index]
                      : ((Pool<T> *)p)->data[index];
    }

    //! iterators themselves can be used in generalized for loops
    //!@note this will not consume the iterator
    DynamicDataIterator<T> begin() const { return *this; }

    DynamicDataIterator<T> end() const { return DynamicDataIterator<T>(); }
        };
    }
}

#endif //SKILL_CPP_COMMON_DYNAMIC_DATA_ITERATOR_H
