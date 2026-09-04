package io.github.daniele21.localllm.integration.servicehost

import android.os.Binder

fun interface CallingProcessSource {
    fun current(): CallingProcess
}

class BinderCallingProcessSource : CallingProcessSource {
    override fun current(): CallingProcess = CallingProcess(
        uid = Binder.getCallingUid(),
        pid = Binder.getCallingPid(),
    )
}
