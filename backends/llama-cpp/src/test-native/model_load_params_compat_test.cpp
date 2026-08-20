#include "model_load_params_compat.h"

#include <cassert>
#include <cstring>
#include <iostream>

namespace {

struct LegacyParams final {
    bool use_mmap = false;
    bool use_mlock = false;
};

enum class CandidateLoadMode : int {
    AUTO = -1,
    NONE = 0,
    MMAP = 1,
    MLOCK = 2,
    MMAP_MLOCK = 3,
    DIRECT_IO = 4,
};

struct CandidateParams final {
    CandidateLoadMode load_mode = CandidateLoadMode::AUTO;
};

void verify_case(
    bool use_mmap,
    bool use_mlock,
    local_llm::LegacyModelLoadMode expected_mode,
    CandidateLoadMode expected_candidate_mode,
    const char* expected_name
) {
    LegacyParams legacy;
    local_llm::apply_legacy_model_load_policy(legacy, use_mmap, use_mlock);
    assert(legacy.use_mmap == use_mmap);
    assert(legacy.use_mlock == use_mlock);

    CandidateParams candidate;
    local_llm::apply_legacy_model_load_policy(candidate, use_mmap, use_mlock);
    assert(candidate.load_mode == expected_candidate_mode);

    const auto resolved = local_llm::resolve_legacy_model_load_mode(use_mmap, use_mlock);
    assert(resolved == expected_mode);
    assert(std::strcmp(local_llm::legacy_model_load_mode_name(resolved), expected_name) == 0);
}

}  // namespace

int main() {
    static_assert(!local_llm::HasLoadMode<LegacyParams>::value);
    static_assert(local_llm::HasLoadMode<CandidateParams>::value);

    verify_case(
        false,
        false,
        local_llm::LegacyModelLoadMode::NONE,
        CandidateLoadMode::NONE,
        "NONE"
    );
    verify_case(
        true,
        false,
        local_llm::LegacyModelLoadMode::MMAP,
        CandidateLoadMode::MMAP,
        "MMAP"
    );
    verify_case(
        false,
        true,
        local_llm::LegacyModelLoadMode::MLOCK,
        CandidateLoadMode::MLOCK,
        "MLOCK"
    );
    verify_case(
        true,
        true,
        local_llm::LegacyModelLoadMode::MMAP_MLOCK,
        CandidateLoadMode::MMAP_MLOCK,
        "MMAP_MLOCK"
    );

    std::cout << "model load params compatibility mapping PASS\n";
    return 0;
}
