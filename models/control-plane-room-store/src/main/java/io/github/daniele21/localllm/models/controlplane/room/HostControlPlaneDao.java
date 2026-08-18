package io.github.daniele21.localllm.models.controlplane.room;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface HostControlPlaneDao {
    @Query("SELECT * FROM hcp_applications ORDER BY application_id")
    List<HostControlPlaneEntities.ApplicationEntity> applications();

    @Query("SELECT * FROM hcp_use_case_revisions ORDER BY use_case_id, revision")
    List<HostControlPlaneEntities.UseCaseEntity> useCases();

    @Query("SELECT * FROM hcp_preset_revisions ORDER BY use_case_id, preset_id, revision")
    List<HostControlPlaneEntities.PresetEntity> presets();

    @Query("SELECT * FROM hcp_binding_revisions ORDER BY binding_id, revision")
    List<HostControlPlaneEntities.BindingEntity> bindings();

    @Query("SELECT * FROM hcp_preset_exposures ORDER BY binding_id, binding_revision, preset_id, preset_revision")
    List<HostControlPlaneEntities.ExposureEntity> exposures();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertApplications(List<HostControlPlaneEntities.ApplicationEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUseCases(List<HostControlPlaneEntities.UseCaseEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPresets(List<HostControlPlaneEntities.PresetEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBindings(List<HostControlPlaneEntities.BindingEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertExposures(List<HostControlPlaneEntities.ExposureEntity> rows);

    @Query("DELETE FROM hcp_preset_exposures")
    void deleteExposures();

    @Query("DELETE FROM hcp_binding_revisions")
    void deleteBindings();

    @Query("DELETE FROM hcp_preset_revisions")
    void deletePresets();

    @Query("DELETE FROM hcp_use_case_revisions")
    void deleteUseCases();

    @Query("DELETE FROM hcp_applications")
    void deleteApplications();
}
