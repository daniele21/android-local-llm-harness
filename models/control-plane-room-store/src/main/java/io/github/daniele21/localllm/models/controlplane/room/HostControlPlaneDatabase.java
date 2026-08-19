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
        version = 2,
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

    public abstract HostControlPlaneDao hostControlPlaneDao();
}
