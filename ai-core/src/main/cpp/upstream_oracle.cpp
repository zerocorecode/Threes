#include <algorithm>
#include <cstdint>
#include <mutex>

#define set_heurweights oracle_set_heurweights
#define init_tables oracle_init_tables
#define score_toplevel_move oracle_score_toplevel_move
#define find_best_move oracle_find_best_move
#define ask_for_move oracle_ask_for_move
#define play_game oracle_play_game
#define main oracle_unused_cli_main
#include "upstream/threes.cpp"
#undef main

static void ensure_oracle_tables() {
    static std::once_flag initialized;
    std::call_once(initialized, oracle_init_tables);
}

extern "C" void threes_original_scores_at_depth(uint64_t board, uint32_t deck, uint16_t tileset, int depth, double* scores) {
    ensure_oracle_tables();
    for (int move = 0; move < 4; ++move) {
        eval_state state;
        state.depth_limit = std::max(0, depth - 1);
        scores[move] = _score_toplevel_move(state, board, deck, tileset, move);
    }
}
