#include <jni.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <exception>
#include <mutex>
#include <new>

namespace {
struct SearchCancelled {};
struct SearchBudgetExpired {};
struct SearchContext {
    JNIEnv* env;
    jobject cancellation;
    jmethodID is_cancelled;
    uint64_t polls = 0;
    int forced_depth = 0;
    bool enforce_budget = false;
    uint64_t visited_nodes = 0;
    uint64_t max_nodes = 0;
    std::chrono::steady_clock::time_point deadline;
};
thread_local SearchContext* active_search = nullptr;

void check_cancellation(SearchContext& context) {
    const bool cancelled = context.env->CallBooleanMethod(context.cancellation, context.is_cancelled);
    if (cancelled || context.env->ExceptionCheck()) throw SearchCancelled{};
}

void threes_poll_search() {
    if (!active_search) return;
    auto& context = *active_search;
    if ((context.polls++ & 1023U) == 0) {
        check_cancellation(context);
    }
    if (context.enforce_budget && (context.visited_nodes >= context.max_nodes ||
        ((context.polls & 63U) == 1 && std::chrono::steady_clock::now() >= context.deadline))) {
        throw SearchBudgetExpired{};
    }
    ++context.visited_nodes;
}

int threes_search_depth(int adaptive_depth) {
    return active_search && active_search->forced_depth >= 0 ? active_search->forced_depth : adaptive_depth;
}

}

#define main threes_unused_cli_main
#include "threes_instrumented.cpp"
#undef main

namespace {
void ensure_tables() {
    static std::once_flag initialized;
    std::call_once(initialized, init_tables);
}

void throw_java(JNIEnv* env, const char* type, const char* message) {
    if (!env->ExceptionCheck()) {
        jclass exception = env->FindClass(type);
        if (exception) env->ThrowNew(exception, message);
    }
}

struct SearchScope {
    explicit SearchScope(SearchContext* context) { active_search = context; }
    ~SearchScope() { active_search = nullptr; }
};
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_zerocode_threes_ai_NneonneoNative_evaluateTimed(
    JNIEnv* env, jobject, jlong board, jint deck, jint tileset,
    jint max_depth, jlong budget_nanos, jlong max_nodes, jobject cancellation
) {
    try {
        const auto started = std::chrono::steady_clock::now();
        ensure_tables();
        jclass supplier = env->GetObjectClass(cancellation);
        jmethodID cancelled = env->GetMethodID(supplier, "getAsBoolean", "()Z");
        env->DeleteLocalRef(supplier);
        if (!cancelled) return nullptr;
        SearchContext context{env, cancellation, cancelled};
        const auto maximum_duration = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::time_point::max() - started).count();
        context.deadline = started + std::chrono::nanoseconds(std::max<int64_t>(1,
            std::min<int64_t>(budget_nanos, maximum_duration)));
        context.max_nodes = static_cast<uint64_t>(std::max<jlong>(1, max_nodes));
        SearchScope scope(&context);
        std::array<jdouble, 7> completed{0, 0, 0, 0, -1, 0, 0};

        for (int depth = 1; depth <= std::max(1, static_cast<int>(max_depth)); ++depth) {
            check_cancellation(context);
            // The first round is the mandatory complete one-move fallback.
            // Never publish only some roots from a round.
            context.enforce_budget = depth > 1;
            if (context.enforce_budget && (std::chrono::steady_clock::now() >= context.deadline ||
                context.visited_nodes >= context.max_nodes)) break;
            // Upstream curdepth excludes the root move; the public timed depth
            // includes it, matching the Kotlin depth convention.
            context.forced_depth = depth - 1;
            std::array<jdouble, 7> iteration{0, 0, 0, 0, -1, 0, 0};
            float best = 0;
            try {
                for (int move = 0; move < 4; ++move) {
                    check_cancellation(context);
                    if (context.enforce_budget && std::chrono::steady_clock::now() >= context.deadline)
                        throw SearchBudgetExpired{};
                    const float value = score_toplevel_move(static_cast<board_t>(board), deck, tileset, move);
                    iteration[move] = value;
                    if (value > best) {
                        best = value;
                        iteration[4] = move;
                    }
                }
            } catch (const SearchBudgetExpired&) {
                break;
            }
            iteration[6] = depth;
            completed = iteration;
        }
        check_cancellation(context);
        completed[5] = static_cast<double>(context.visited_nodes);
        jdoubleArray values = env->NewDoubleArray(7);
        if (values) env->SetDoubleArrayRegion(values, 0, 7, completed.data());
        return values;
    } catch (const SearchCancelled&) {
        throw_java(env, "java/util/concurrent/CancellationException", "nneonneo timed search cancelled");
    } catch (const std::bad_alloc&) {
        throw_java(env, "java/lang/OutOfMemoryError", "nneonneo timed search allocation failed");
    } catch (const std::exception& error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
    return nullptr;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_zerocode_threes_ai_NneonneoNative_move(JNIEnv* env, jobject, jlong board, jint direction) {
    ensure_tables();
    int changed = 0;
    const board_t moved = execute_move(direction, static_cast<board_t>(board), &changed);
    const jlong result[2] = {static_cast<jlong>(moved), changed};
    jlongArray values = env->NewLongArray(2);
    if (values) env->SetLongArrayRegion(values, 0, 2, result);
    return values;
}

#ifdef THREES_ORACLE_AVAILABLE
extern "C" void threes_original_scores_at_depth(uint64_t, uint32_t, uint16_t, int, double*);
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_zerocode_threes_ai_NneonneoNative_originalScoresAtDepth(
    JNIEnv* env, jobject, jlong board, jint deck, jint tileset, jint depth
) {
    jdouble result[4];
    threes_original_scores_at_depth(static_cast<uint64_t>(board), deck, tileset, depth, result);
    jdoubleArray values = env->NewDoubleArray(4);
    if (values) env->SetDoubleArrayRegion(values, 0, 4, result);
    return values;
}
#endif
