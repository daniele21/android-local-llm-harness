#include "flash_attention_params.h"

#include <cassert>

namespace {

enum class FakeFlashAttentionType : int {
    AUTO = -1,
    DISABLED = 0,
    ENABLED = 1,
};

struct FakeContextParams {
    FakeFlashAttentionType flash_attn_type = FakeFlashAttentionType::DISABLED;
};

void assert_mode(int value, FakeFlashAttentionType expected) {
    FakeContextParams params;
    assert(local_llm::apply_flash_attention_mode(params, value));
    assert(params.flash_attn_type == expected);
}

}  // namespace

int main() {
    assert_mode(-1, FakeFlashAttentionType::AUTO);
    assert_mode(0, FakeFlashAttentionType::DISABLED);
    assert_mode(1, FakeFlashAttentionType::ENABLED);

    FakeContextParams invalid;
    invalid.flash_attn_type = FakeFlashAttentionType::ENABLED;
    assert(!local_llm::apply_flash_attention_mode(invalid, 2));
    assert(invalid.flash_attn_type == FakeFlashAttentionType::ENABLED);
    assert(!local_llm::apply_flash_attention_mode(invalid, -2));
    assert(invalid.flash_attn_type == FakeFlashAttentionType::ENABLED);

    return 0;
}
