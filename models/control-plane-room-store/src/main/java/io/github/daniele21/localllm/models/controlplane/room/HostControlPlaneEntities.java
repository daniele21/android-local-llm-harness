package io.github.daniele21.localllm.models.controlplane.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

public final class HostControlPlaneEntities {
    private HostControlPlaneEntities() {}

    @Entity(tableName = "hcp_applications", primaryKeys = {"application_id"})
    public static final class ApplicationEntity {
        @NonNull
        @ColumnInfo(name = "application_id")
        public final String applicationId;

        @ColumnInfo(name = "package_name")
        public final String packageName;

        @ColumnInfo(name = "signer_sha256")
        public final String signerSha256;

        @ColumnInfo(name = "display_name")
        public final String displayName;

        @ColumnInfo(name = "state")
        public final String state;

        @ColumnInfo(name = "first_seen_at_epoch_ms")
        public final long firstSeenAtEpochMs;

        @ColumnInfo(name = "last_seen_at_epoch_ms")
        public final long lastSeenAtEpochMs;

        public ApplicationEntity(
                @NonNull String applicationId,
                String packageName,
                String signerSha256,
                String displayName,
                String state,
                long firstSeenAtEpochMs,
                long lastSeenAtEpochMs) {
            this.applicationId = applicationId;
            this.packageName = packageName;
            this.signerSha256 = signerSha256;
            this.displayName = displayName;
            this.state = state;
            this.firstSeenAtEpochMs = firstSeenAtEpochMs;
            this.lastSeenAtEpochMs = lastSeenAtEpochMs;
        }
    }

    @Entity(tableName = "hcp_use_case_revisions", primaryKeys = {"use_case_id", "revision"})
    public static final class UseCaseEntity {
        @NonNull
        @ColumnInfo(name = "use_case_id")
        public final String useCaseId;

        @ColumnInfo(name = "revision")
        public final int revision;

        @ColumnInfo(name = "display_name")
        public final String displayName;

        @ColumnInfo(name = "description")
        public final String description;

        @ColumnInfo(name = "output_mode")
        public final String outputMode;

        @ColumnInfo(name = "session_kind")
        public final String sessionKind;

        @ColumnInfo(name = "reasoning_supported")
        public final boolean reasoningSupported;

        @ColumnInfo(name = "minimum_context_tokens")
        public final int minimumContextTokens;

        @Nullable
        @ColumnInfo(name = "max_input_characters")
        public final Integer maxInputCharacters;

        @Nullable
        @ColumnInfo(name = "max_json_schema_characters")
        public final Integer maxJsonSchemaCharacters;

        @ColumnInfo(name = "state")
        public final String state;

        public UseCaseEntity(
                @NonNull String useCaseId,
                int revision,
                String displayName,
                String description,
                String outputMode,
                String sessionKind,
                boolean reasoningSupported,
                int minimumContextTokens,
                @Nullable Integer maxInputCharacters,
                @Nullable Integer maxJsonSchemaCharacters,
                String state) {
            this.useCaseId = useCaseId;
            this.revision = revision;
            this.displayName = displayName;
            this.description = description;
            this.outputMode = outputMode;
            this.sessionKind = sessionKind;
            this.reasoningSupported = reasoningSupported;
            this.minimumContextTokens = minimumContextTokens;
            this.maxInputCharacters = maxInputCharacters;
            this.maxJsonSchemaCharacters = maxJsonSchemaCharacters;
            this.state = state;
        }
    }

    @Entity(tableName = "hcp_preset_revisions", primaryKeys = {"use_case_id", "preset_id", "revision"})
    public static final class PresetEntity {
        @NonNull
        @ColumnInfo(name = "use_case_id")
        public final String useCaseId;

        @NonNull
        @ColumnInfo(name = "preset_id")
        public final String presetId;

        @ColumnInfo(name = "revision")
        public final int revision;

        @ColumnInfo(name = "display_name")
        public final String displayName;

        @ColumnInfo(name = "description")
        public final String description;

        @ColumnInfo(name = "creation_source")
        public final String creationSource;

        @ColumnInfo(name = "state")
        public final String state;

        @Nullable
        @ColumnInfo(name = "model_profile_id")
        public final String modelProfileId;

        @ColumnInfo(name = "inference_preset_id")
        public final String inferencePresetId;

        @ColumnInfo(name = "inference_preset_version")
        public final int inferencePresetVersion;

        @Nullable
        @ColumnInfo(name = "context_tokens")
        public final Integer contextTokens;

        @ColumnInfo(name = "retain_model_warm_ms")
        public final long retainModelWarmMs;

        @ColumnInfo(name = "reuse_stateless_context")
        public final boolean reuseStatelessContext;

        @ColumnInfo(name = "enable_prefix_snapshot")
        public final boolean enablePrefixSnapshot;

        @ColumnInfo(name = "enable_deterministic_result_cache")
        public final boolean enableDeterministicResultCache;

        @Nullable
        @ColumnInfo(name = "generation_max_output_tokens")
        public final Integer generationMaxOutputTokens;

        @Nullable
        @ColumnInfo(name = "generation_temperature")
        public final Float generationTemperature;

        @Nullable
        @ColumnInfo(name = "generation_top_p")
        public final Float generationTopP;

        @Nullable
        @ColumnInfo(name = "generation_top_k")
        public final Integer generationTopK;

        @Nullable
        @ColumnInfo(name = "generation_min_p")
        public final Float generationMinP;

        @Nullable
        @ColumnInfo(name = "generation_presence_penalty")
        public final Float generationPresencePenalty;

        @Nullable
        @ColumnInfo(name = "generation_repeat_penalty")
        public final Float generationRepeatPenalty;

        @Nullable
        @ColumnInfo(name = "generation_repeat_last_n")
        public final Integer generationRepeatLastN;

        @Nullable
        @ColumnInfo(name = "generation_thinking_mode")
        public final String generationThinkingMode;

        @Nullable
        @ColumnInfo(name = "generation_seed_mode")
        public final String generationSeedMode;

        @Nullable
        @ColumnInfo(name = "generation_fixed_seed")
        public final Long generationFixedSeed;

        public PresetEntity(
                @NonNull String useCaseId,
                @NonNull String presetId,
                int revision,
                String displayName,
                String description,
                String creationSource,
                String state,
                @Nullable String modelProfileId,
                String inferencePresetId,
                int inferencePresetVersion,
                @Nullable Integer contextTokens,
                long retainModelWarmMs,
                boolean reuseStatelessContext,
                boolean enablePrefixSnapshot,
                boolean enableDeterministicResultCache,
                @Nullable Integer generationMaxOutputTokens,
                @Nullable Float generationTemperature,
                @Nullable Float generationTopP,
                @Nullable Integer generationTopK,
                @Nullable Float generationMinP,
                @Nullable Float generationPresencePenalty,
                @Nullable Float generationRepeatPenalty,
                @Nullable Integer generationRepeatLastN,
                @Nullable String generationThinkingMode,
                @Nullable String generationSeedMode,
                @Nullable Long generationFixedSeed) {
            this.useCaseId = useCaseId;
            this.presetId = presetId;
            this.revision = revision;
            this.displayName = displayName;
            this.description = description;
            this.creationSource = creationSource;
            this.state = state;
            this.modelProfileId = modelProfileId;
            this.inferencePresetId = inferencePresetId;
            this.inferencePresetVersion = inferencePresetVersion;
            this.contextTokens = contextTokens;
            this.retainModelWarmMs = retainModelWarmMs;
            this.reuseStatelessContext = reuseStatelessContext;
            this.enablePrefixSnapshot = enablePrefixSnapshot;
            this.enableDeterministicResultCache = enableDeterministicResultCache;
            this.generationMaxOutputTokens = generationMaxOutputTokens;
            this.generationTemperature = generationTemperature;
            this.generationTopP = generationTopP;
            this.generationTopK = generationTopK;
            this.generationMinP = generationMinP;
            this.generationPresencePenalty = generationPresencePenalty;
            this.generationRepeatPenalty = generationRepeatPenalty;
            this.generationRepeatLastN = generationRepeatLastN;
            this.generationThinkingMode = generationThinkingMode;
            this.generationSeedMode = generationSeedMode;
            this.generationFixedSeed = generationFixedSeed;
        }
    }

    @Entity(tableName = "hcp_binding_revisions", primaryKeys = {"binding_id", "revision"})
    public static final class BindingEntity {
        @NonNull
        @ColumnInfo(name = "binding_id")
        public final String bindingId;

        @ColumnInfo(name = "revision")
        public final int revision;

        @ColumnInfo(name = "application_id")
        public final String applicationId;

        @ColumnInfo(name = "use_case_id")
        public final String useCaseId;

        @ColumnInfo(name = "enabled")
        public final boolean enabled;

        @ColumnInfo(name = "is_default")
        public final boolean isDefault;

        public BindingEntity(
                @NonNull String bindingId,
                int revision,
                String applicationId,
                String useCaseId,
                boolean enabled,
                boolean isDefault) {
            this.bindingId = bindingId;
            this.revision = revision;
            this.applicationId = applicationId;
            this.useCaseId = useCaseId;
            this.enabled = enabled;
            this.isDefault = isDefault;
        }
    }

    @Entity(
            tableName = "hcp_preset_exposures",
            primaryKeys = {"binding_id", "binding_revision", "preset_id", "preset_revision"})
    public static final class ExposureEntity {
        @NonNull
        @ColumnInfo(name = "binding_id")
        public final String bindingId;

        @ColumnInfo(name = "binding_revision")
        public final int bindingRevision;

        @NonNull
        @ColumnInfo(name = "preset_id")
        public final String presetId;

        @ColumnInfo(name = "preset_revision")
        public final int presetRevision;

        @ColumnInfo(name = "is_default")
        public final boolean isDefault;

        public ExposureEntity(
                @NonNull String bindingId,
                int bindingRevision,
                @NonNull String presetId,
                int presetRevision,
                boolean isDefault) {
            this.bindingId = bindingId;
            this.bindingRevision = bindingRevision;
            this.presetId = presetId;
            this.presetRevision = presetRevision;
            this.isDefault = isDefault;
        }
    }
}
