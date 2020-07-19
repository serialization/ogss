//
// Created by Timm Felden on 24.11.15.
//

#ifndef OGSS_COMMON_BOOK_H
#define OGSS_COMMON_BOOK_H

#include "../common.h"
#include <cassert>
#include <vector>

namespace ogss {
namespace internal {
/**
 * management of T instances; basically a freelist approach
 * @author Timm Felden
 */
template <class T> class Book {
    // TODO static_assert(sizeof(T*) <= sizeof(T), "T is too small for Books");
    //! @todo optimize to T*
    std::vector<T *> freelist;
    //! @invariant: if not current page then, T is used or T is in freeList
    std::vector<T *> pages;

    T *currentPage;
    /**
     * we don't need an index, because we will only have a variable page size
     * for the first page
     */
    int currentPageRemaining;

    //! size of unhinted pages
    static const int defaultPageSize = 128;
    static_assert(defaultPageSize > 0, "pages must have positive size");

  public:
    /**
     * a new book that has pre allocated instances for the expected size
     */
    explicit Book(ObjectID expectedSize = defaultPageSize) :
      freelist(),
      pages(),
      currentPage(expectedSize ? (T *)std::calloc(static_cast<size_t>(expectedSize), sizeof(T))
                               : nullptr),
      currentPageRemaining(0) {
        if (currentPage)
            pages.push_back(currentPage);
    }

    virtual ~Book() {
        for (T *page : pages) {
            std::free(page);
        }
    }

    /**
     * returns the first page. only to be used on initial allocateInstances
     * call!
     */
    T *firstPage() {
        assert(pages[0] == currentPage);
        return currentPage;
    }

    /**
     * return the next free instance
     *
     * @note must never be called, while using the first page
     */
    T *next() {
        // first we try to take from current page
        if (currentPageRemaining) {
            return currentPage + (defaultPageSize - currentPageRemaining--);
        } else if (freelist.size()) {
            // deplete freelist before allocating a new page
            T *r = freelist.back();
            freelist.pop_back();
            memset((void *)r, 0, sizeof(T));
            return r;
        } else {
            // we have to allocate a new page
            currentPage = (T *)std::calloc(defaultPageSize, sizeof(T));
            pages.push_back(currentPage);
            // return first object
            currentPageRemaining = defaultPageSize - 1;
            return currentPage;
        }
    }

    /**
     * recycle the argument instance
     */
    void free(T *target) { freelist.push_back(target); }
};
} // namespace internal
} // namespace ogss

#endif // OGSS_COMMON_BOOK_H

