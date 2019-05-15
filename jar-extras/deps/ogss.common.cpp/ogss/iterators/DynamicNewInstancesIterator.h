//
// Created by Timm Felden on 28.12.15.
//

#ifndef SKILL_CPP_COMMON_DYNAMIC_NEW_INSTANCES_ITERATOR_H
#define SKILL_CPP_COMMON_DYNAMIC_NEW_INSTANCES_ITERATOR_H

#include <iterator>


namespace skill {
    namespace internal {
        template<class T, class B>
        class StoragePool;
    }
    using internal::StoragePool;

    namespace iterators {
        /**
         * Iterates efficiently over dynamic new instances of a pool.
         *
         * Like second phase of dynamic data iterator.
         *
         * @author Timm Felden
         *
         * @note apparently, this implementation is completely broken; see ada for correct one
         */
        template<class T, class B>
        class DynamicNewInstancesIterator :
                public std::iterator<std::input_iterator_tag, T> {

            //! current type
            TypeHierarchyIterator ts;
            SKilLID index;
            SKilLID end;

        public:
            DynamicNewInstancesIterator(const StoragePool<T, B> *p)
                    : ts(p), index(0), end(0) {

                while (ts.hasNext()) {
                    const StoragePool<T, B> *t = (const StoragePool<T, B> *) ts.next();
                    if (t->newObjects.size() != 0) {
                        end = t->newObjects.size();
                        break;
                    }
                }
            }

            DynamicNewInstancesIterator(const DynamicNewInstancesIterator<T, B> &iter)
                    : ts(iter.ts), index(iter.index), end(iter.end) { }

            DynamicNewInstancesIterator &operator++() {
                if (secondIndex <= lastBlock) {
                    // @note increment happens before access, because we shifted data by 1
                    index++;
                    if (index == end) {
                        const StoragePool<T, B> &p = (const StoragePool<T, B> &) *ts;
                        while (index == end && secondIndex < lastBlock) {
                            const auto &b = p.blocks[secondIndex];
                            index = b.bpo;
                            end = index + b.dynamicCount;
                            secondIndex++;
                        }
                        // mode switch, if there is no other block
                        if (index == end && secondIndex == lastBlock) {
                            secondIndex++;
                            while (ts.hasNext()) {
                                const StoragePool<T, B> *t = (const StoragePool<T, B> *) ts.next();
                                if (t->newObjects.size() != 0) {
                                    index = 0;
                                    end = t->newObjects.size();
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    index++;
                    if (index == end) {
                        while (ts.hasNext()) {
                            const StoragePool<T, B> *t = (const StoragePool<T, B> *) ts.next();
                            if (t->newObjects.size() != 0) {
                                index = 0;
                                end = t->newObjects.size();
                                break;
                            }
                        }
                    }
                }
                return *this;
            }

            DynamicNewInstancesIterator operator++(int) {
                DynamicNewInstancesIterator tmp(*this);
                operator++();
                return tmp;
            }

            //! move to next position and return current element
            T *next() {
                if (secondIndex <= lastBlock) {
                    const StoragePool<T, B> &p = (const StoragePool<T, B> &) *ts;
                    // @note increment happens before access, because we shifted data by 1
                    index++;
                    T *r = p.data[index];
                    if (index == end) {
                        while (index == end && secondIndex < lastBlock) {
                            const auto &b = p.blocks[secondIndex];
                            index = b.bpo;
                            end = index + b.dynamicCount;
                            secondIndex++;
                        }
                        // mode switch, if there is no other block
                        if (index == end && secondIndex == lastBlock) {
                            secondIndex++;
                            while (ts.hasNext()) {
                                const StoragePool<T, B> *t = (const StoragePool<T, B> *) ts.next();
                                if (t->newObjects.size() != 0) {
                                    index = 0;
                                    end = t->newObjects.size();
                                    break;
                                }
                            }
                        }
                    }
                    return r;
                } else {
                    const StoragePool<T, B> &p = (const StoragePool<T, B> &) *ts;
                    T *r = p.newObjects[index];
                    index++;
                    if (index == end) {
                        while (ts.hasNext()) {
                            const StoragePool<T, B> *t = (const StoragePool<T, B> *) ts.next();
                            if (t->newObjects.size() != 0) {
                                index = 0;
                                end = t->newObjects.size();
                                break;
                            }
                        }
                    }
                    return r;
                }
            }

            //! @return true, iff another element can be returned
            bool hasNext() const {
                return index != end;
            }

            bool operator==(const DynamicNewInstancesIterator<T, B> &iter) const {
                return ts == iter.ts &&
                       secondIndex == iter.secondIndex &&
                       lastBlock == iter.lastBlock &&
                       index == iter.index &&
                       end == iter.end;
            }

            bool operator!=(const DynamicNewInstancesIterator<T, B> &rhs) const {
                return !(this->operator==(rhs));
            }

            T &operator*() const {
                const StoragePool<T, B> &p = (const StoragePool<T, B> &) *ts;
                // @note increment happens before access, because we shifted data by 1
                return *(index < end ? p.data[index + 1] : p.newObjects[secondIndex]);
            }

            T* operator->() const {
                const StoragePool<T, B> &p = (const StoragePool<T, B> &) *ts;
                return index < end ? p.data[index + 1] : p.newObjects[secondIndex];
            }
        };
    }
}

#endif //SKILL_CPP_COMMON_DYNAMIC_NEW_INSTANCES_ITERATOR_H
