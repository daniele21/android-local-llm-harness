#pragma once

#include <type_traits>
#include <utility>

namespace local_llm {

// Candidate 60addddf3c567c43ec3caf70fc953fba3572d96f replaces the legacy
// use_mmap/use_mlock booleans with llama_load_mode. Keep product semantics stable
// across both APIs before any AUTO policy is evaluated.
enum class LegacyModelLoadMode : int {
    NONE = 0,
    MMAP = 1,
    MLOCK = 2,
    MMAP_MLOCK = 3,
};

template <typename Params, typename = void>
struct HasLoadMode : std::false_type {};

template <typename Params>
struct HasLoadMode<Params, std::void_t<decltype(std::declval<Params&>().load_mode)>> : std::true_type {};

inline LegacyModelLoadMode resolve_legacy_model_load_mode(bool use_mmap, bool use_mlock) {
    if (use_mmap && use_mlock) {
        return LegacyModelLoadMode::MMAP_MLOCK;
    }
    if (use_mmap) {
        return LegacyModelLoadMode::MMAP;
    }
    if (use_mlock) {
        return LegacyModelLoadMode::MLOCK;
    }
    return LegacyModelLoadMode::NONE;
}

inline const char* legacy_model_load_mode_name(LegacyModelLoadMode mode) {
    switch (mode) {
        case LegacyModelLoadMode::NONE:
            return "NONE";
        case LegacyModelLoadMode::MMAP:
            return "MMAP";
        case LegacyModelLoadMode::MLOCK:
            return "MLOCK";
        case LegacyModelLoadMode::MMAP_MLOCK:
            return "MMAP_MLOCK";
    }
    return "UNKNOWN";
}

template <typename Params, std::enable_if_t<!HasLoadMode<Params>::value, int> = 0>
void apply_legacy_model_load_policy(Params& params, bool use_mmap, bool use_mlock) {
    params.use_mmap = use_mmap;
    params.use_mlock = use_mlock;
}

template <typename Params, std::enable_if_t<HasLoadMode<Params>::value, int> = 0>
void apply_legacy_model_load_policy(Params& params, bool use_mmap, bool use_mlock) {
    const LegacyModelLoadMode mode = resolve_legacy_model_load_mode(use_mmap, use_mlock);
    params.load_mode = static_cast<decltype(params.load_mode)>(static_cast<int>(mode));
}

}  // namespace local_llm
