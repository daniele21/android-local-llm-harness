package io.github.daniele21.localllm.models.controlplane.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {
            HostControlPlaneEntities.ApplicationEntity.class,
            HostControlPlaneEntities.UseCaseEntity.class,
            HostControlPlaneEntities.PresetEntity.class,
            HostControlPlaneEntities.BindingEntity.class,
            HostControlPlaneEntities.ExposureEntity.class
        },
        version = 1,
        exportSchema = true)
public abstract class HostControlPlaneDatabase extends RoomDatabase {
    public abstract HostControlPlaneDao hostControlPlaneDao();
}
