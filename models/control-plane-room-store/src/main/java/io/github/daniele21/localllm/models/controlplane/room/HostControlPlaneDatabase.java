package io.github.daniele21.localllm.models.controlplane.room;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
            HostControlPlaneEntities.ApplicationEntity.class,
            HostControlPlaneEntities.UseCaseEntity.class,
            HostControlPlaneEntities.PresetEntity.class,
            HostControlPlaneEntities.BindingEntity.class,
            HostControlPlaneEntities.ExposureEntity.class
        },
        version = 3,
        exportSchema = true)
public abstract class HostControlPlaneDatabase extends RoomDatabase {
    public static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL(
                            "ALTER TABLE `hcp_binding_revisions` "
                                    + "ADD COLUMN `is_default` INTEGER NOT NULL DEFAULT 0");
                }
            };

    public static final Migration MIGRATION_2_3 =
            new Migration(2, 3) {
                @Override
                public void migrate(@NonNull SupportSQLiteDatabase database) {
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_max_output_tokens` INTEGER");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_temperature` REAL");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_top_p` REAL");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_top_k` INTEGER");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_min_p` REAL");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_presence_penalty` REAL");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_repeat_penalty` REAL");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_repeat_last_n` INTEGER");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_thinking_mode` TEXT");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_seed_mode` TEXT");
                    database.execSQL("ALTER TABLE `hcp_preset_revisions` ADD COLUMN `generation_fixed_seed` INTEGER");
                }
            };

    public abstract HostControlPlaneDao hostControlPlaneDao();
}
