package io.github.daniele21.localllm.integration.servicehost

fun interface ClientDeathLink {
    fun unlink()
}

fun interface ClientLifecycleLinker {
    fun link(onDeath: () -> Unit): ClientDeathLink?
}
