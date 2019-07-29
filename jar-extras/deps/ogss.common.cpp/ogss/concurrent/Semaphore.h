//
// Created by Timm Felden on 20.05.19.
//

#ifndef OGSS_COMMON_CPP_CONCURRENT_SEMAPHORE_H
#define OGSS_COMMON_CPP_CONCURRENT_SEMAPHORE_H

#include <cassert>

namespace ogss {
    namespace concurrent {
        /**
         * A simple semaphore implementation. Reuse as you wish. In fact, this should be a part of namespace std.
         * @note the semaphore can have negative available permits to allow implementation of dynamic barriers
         *
         * @author Timm Felden
         */
        class Semaphore {
            std::mutex mx;
            std::condition_variable cv;
            std::int32_t status;

        public:
            // the default semaphore has no permit available; though, there are protocols that require existence of
            // initial permits without waiting threads
            explicit Semaphore(std::int32_t permits = 0) : mx(), cv(), status(permits) {}

            // allow subclassing
            virtual ~Semaphore() = default;

            /**
             * release a permit and notify waiting threads if status is raised above 0
             */
            void release() {
                std::lock_guard <std::mutex> lock(mx);
                ++status;
                if (0 < status)
                    cv.notify_one();
            }

            /**
             * wait until a permit is available
             */
            void take() {
                std::unique_lock <std::mutex> lock(mx);
                // on while instead of if: threads can be woken up accidentally
                // @see https://en.wikipedia.org/wiki/Spurious_wakeup
                while (0 >= status)
                    cv.wait(lock);
                --status;
            }

            /**
             * wait until the argument number of permits is available; takes all available permits on wakeup to reduce
             * communication costs
             *
             * @param n number of permits to take; must be zero or positive
             */
            void takeMany(std::int32_t n) {
                assert(n >= 0);
                while (n > 0) {

                    //TODO !!!optimize!!!
                    take();
                    n--;
                }
            }

            /**
             * try to take a permit without blocking
             *
             * @return true iff a permit was taken
             */
            bool tryTake() {
                std::lock_guard <std::mutex> lock(mx);
                if (0 < status) {
                    --status;
                    return true;
                }
                return false;
            }

            /**
             * decrement status by n permits without waiting
             *
             * @note this functionality is required in some protocols and is *very* likely not what you want to do
             * @note n must be >= 0; otherwise, it might be necessary to wake up threads
             * @note there is no fill function, because it would be identical to calling release in a counting loop
             */
            void drain(int n) {
                assert(n >= 0);
                std::lock_guard <std::mutex> lock(mx);
                status -= n;
            }

            /**
             * @return the current number of permits
             * @note this action is not synchronized because its result is subject to data races anyway in the sense that
             * the status may have changed before any action is taken by the caller
             */
            std::int32_t permits() const {
                return status;
            }

            /**
             * release all waiting threads; the semaphore will be left in an unpredictable state afterwards
             *
             * the sole purpose of this method is to allow graceful error handling
             */
            void shutdown() {
                {
                    std::lock_guard <std::mutex> lock(mx);
                    status = std::numeric_limits<std::int32_t>::max();
                }
                cv.notify_all();
            }


            /**
             * A permit of the argument semaphore is released if this object runs out of scope.
             */
            class ScopedPermit {
                Semaphore *target;

            public:
                explicit ScopedPermit(Semaphore *target) : target(target) {}

                ~ScopedPermit() {
                    if (target)
                        target->release();
                }

                /**
                 * abort the action, i.e. automatic release of the permit
                 */
                void abort() {
                    target = nullptr;
                }
            };
        };
    }
}

#endif //OGSS_COMMON_CPP_CONCURRENT_SEMAPHORE_H
