package io.github.daniele21.localllm.integration.servicehost

import android.os.IBinder
import android.os.RemoteException
import io.github.daniele21.localllm.transport.binder.contract.IClientLifecycle

class BinderClientLifecycleLinker(private val lifecycle: IClientLifecycle) : ClientLifecycleLinker {
    override fun link(onDeath: () -> Unit): ClientDeathLink? {
        val binder = lifecycle.asBinder()
        val recipient = IBinder.DeathRecipient(onDeath)
        return try {
            binder.linkToDeath(recipient, 0)
            ClientDeathLink { binder.unlinkToDeath(recipient, 0) }
        } catch (_: RemoteException) {
            null
        }
    }
}
