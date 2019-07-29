//
// Created by Timm Felden on 20.05.19.
//

#ifndef OGSS_COMMON_CPP_CONCURRENT_POOL_H
#define OGSS_COMMON_CPP_CONCURRENT_POOL_H

#include <condition_variable>
#include <deque>
#include <mutex>
#include <thread>
#include <vector>

namespace ogss {
namespace concurrent {

/**
 * A job to be executed by the pool. This is a very explicit alternative to
 * lambda expressions. We use it here, because lambda-based approaches tend to
 * have non-obvious semantics and we want to keep our architecture similar to
 * architectures of other OGSS implementations. Also, the combination of
 * emplace, references, lambdas, friends, pointer to members and life times can
 * yield messy solutions.
 *
 * @author Timm Felden
 */
struct Job {
    /**
     * called once the Job has finished execution and is hence removed from the
     * pool
     * @note the thread executing the destructor is not specified
     * @note if the executing pool is deleted before run was called, the job
     * will be deleted without ever calling run
     */
    virtual ~Job() = default;

    /// this action is called from an arbitrary worker thread
    virtual void run() = 0;
};

/**
 * A fix-sized pool of worker threads to execute non-blocking jobs.
 * The pool is intended to be used inside a single OGFile.
 * Therefore, exceptions raised by jobs will be accumulated in the pool.
 *
 * @author Timm Felden
 */
class Pool final {
    const size_t workerCount;
    std::thread **const workers;

    std::mutex mx;
    std::condition_variable cv;

    std::deque<Job *> jobs;

    //! if an exception would otherwise kill a worker thread, it is enqueued
    //! here instead
    std::vector<std::string> errors;

    bool shutdown;

  public:
    Pool();

    /**
     * shutdown the pool
     */
    ~Pool();

    /**
     * transfer ownership of a heap allocated job to this pool
     * @note run can be called from multiple threads, as long as it cannot
     * happen in parallel with ~Pool
     * @note the job submitted must terminate eventually. Otherwise, the pool
     * will not shut down
     * @note running nullptr has no effect
     */
    void run(Job *job) {
        if (job) {
            {
                std::lock_guard<std::mutex> lock(mx);
                jobs.push_back(job);
            }
            cv.notify_one();
        }
    }

    /**
     * Start all argument jobs at once. In essence the same as run for each
     * element of the argument iterable but with reduced communication overhead
     * if more jobs than std::thread::hardware_concurrency are submitted.
     */
    template <typename C> void runAll(C js) {
        {
            std::lock_guard<std::mutex> lock(mx);
            for (Job *j : js) {
                if (j)
                    jobs.push_back(j);
            }
        }
        cv.notify_all();
    }

    /**
     * Check if the pool holds errors.
     */
    bool hasErrors() const { return !errors.empty(); }

    /**
     * Take all error messages.
     */
    void takeErrors(std::vector<std::string> &errors);
};
} // namespace concurrent
} // namespace ogss

#endif // OGSS_COMMON_CPP_CONCURRENT_POOL_H
