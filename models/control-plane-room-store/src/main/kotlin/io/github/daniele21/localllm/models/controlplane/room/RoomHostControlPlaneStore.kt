package io.github.daniele21.localllm.models.controlplane.room

import io.github.daniele21.localllm.models.HostControlPlaneState
import io.github.daniele21.localllm.models.HostControlPlaneStore
import io.github.daniele21.localllm.models.HostControlPlaneTransaction
import java.util.concurrent.Callable

class RoomHostControlPlaneStore(private val database: HostControlPlaneDatabase) : HostControlPlaneStore {
    private val dao = database.hostControlPlaneDao()

    override fun snapshot(): HostControlPlaneState = readState()

    override fun replace(state: HostControlPlaneState) {
        val canonical = state.canonical()
        database.runInTransaction {
            replaceEntities(HostControlPlaneEntityMapper.toEntities(canonical))
        }
    }

    override fun transact(transaction: HostControlPlaneTransaction): HostControlPlaneState = database.runInTransaction(
        Callable {
            val current = readState()
            val next = transaction.apply(current).canonical()
            replaceEntities(HostControlPlaneEntityMapper.toEntities(next))
            next
        },
    )

    private fun readState(): HostControlPlaneState = HostControlPlaneEntityMapper.fromEntities(
        HostControlPlaneEntitySet(
            applications = dao.applications(),
            useCases = dao.useCases(),
            presets = dao.presets(),
            bindings = dao.bindings(),
            exposures = dao.exposures(),
        ),
    )

    private fun replaceEntities(entities: HostControlPlaneEntitySet) {
        dao.deleteExposures()
        dao.deleteBindings()
        dao.deletePresets()
        dao.deleteUseCases()
        dao.deleteApplications()

        if (entities.applications.isNotEmpty()) dao.insertApplications(entities.applications)
        if (entities.useCases.isNotEmpty()) dao.insertUseCases(entities.useCases)
        if (entities.presets.isNotEmpty()) dao.insertPresets(entities.presets)
        if (entities.bindings.isNotEmpty()) dao.insertBindings(entities.bindings)
        if (entities.exposures.isNotEmpty()) dao.insertExposures(entities.exposures)
    }
}
