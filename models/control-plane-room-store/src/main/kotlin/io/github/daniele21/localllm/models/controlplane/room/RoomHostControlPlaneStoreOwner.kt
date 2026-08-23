package io.github.daniele21.localllm.models.controlplane.room

import android.content.Context
import androidx.room.Room
import io.github.daniele21.localllm.models.HostControlPlaneStore

class RoomHostControlPlaneStoreOwner private constructor(
    val store: HostControlPlaneStore,
    private val database: HostControlPlaneDatabase,
) : AutoCloseable {
    override fun close() {
        database.close()
    }

    companion object {
        fun open(context: Context, databaseName: String): RoomHostControlPlaneStoreOwner {
            require(databaseName.isNotBlank()) { "Control-plane database name must not be blank" }
            val database = Room.databaseBuilder(
                context.applicationContext,
                HostControlPlaneDatabase::class.java,
                databaseName,
            ).addMigrations(HostControlPlaneDatabase.MIGRATION_1_2).build()
            return RoomHostControlPlaneStoreOwner(
                store = RoomHostControlPlaneStore(database),
                database = database,
            )
        }
    }
}
