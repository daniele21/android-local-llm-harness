package io.github.daniele21.localllm.transport.binder.client

import io.github.daniele21.localllm.transport.binder.contract.BinderProtocolV1
import io.github.daniele21.localllm.transport.binder.contract.IConsumerLocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.ILocalLlmService
import io.github.daniele21.localllm.transport.binder.contract.ProtocolInfoParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Proxy

class AidlSharedRuntimeRemoteServiceTest {
    @Test
    fun `construction and protocol negotiation do not touch consumer API transaction`() {
        var consumerApiReads = 0
        val protocol = legacyProtocolInfo()
        val delegate = localLlmProxy { methodName ->
            when (methodName) {
                "getProtocolInfo" -> protocol

                "getConsumerApi" -> {
                    consumerApiReads += 1
                    throw AssertionError("consumer API must not be touched before compatibility negotiation")
                }

                "asBinder" -> null

                else -> throw AssertionError("Unexpected ILocalLlmService call: $methodName")
            }
        }

        val service = AidlSharedRuntimeRemoteService(delegate)

        assertEquals(0, consumerApiReads)
        assertSame(protocol, service.protocolInfo())
        assertEquals(0, consumerApiReads)
    }

    @Test
    fun `consumer API transaction is deferred until consumer endpoint is requested`() {
        var consumerApiReads = 0
        val consumerDelegate = consumerProxy()
        val delegate = localLlmProxy { methodName ->
            when (methodName) {
                "getProtocolInfo" -> compatibleProtocolInfo()

                "getConsumerApi" -> {
                    consumerApiReads += 1
                    consumerDelegate
                }

                "asBinder" -> null

                else -> throw AssertionError("Unexpected ILocalLlmService call: $methodName")
            }
        }

        val service = AidlSharedRuntimeRemoteService(delegate)

        assertEquals(0, consumerApiReads)
        service.protocolInfo()
        assertEquals(0, consumerApiReads)
        service.consumer
        service.consumer
        assertEquals(1, consumerApiReads)
    }

    private fun legacyProtocolInfo() = ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = 0,
        minSupportedMinor = 0,
        supportedFeatures = (BinderProtocolV1.KNOWN_FEATURES - BinderProtocolV1.FEATURE_CONSUMER_API_V1).sorted(),
        hostBuildId = "legacy-host",
    )

    private fun compatibleProtocolInfo() = ProtocolInfoParcel(
        protocolMajor = BinderProtocolV1.MAJOR,
        protocolMinor = BinderProtocolV1.MINOR,
        minSupportedMinor = BinderProtocolV1.MIN_SUPPORTED_MINOR,
        supportedFeatures = BinderProtocolV1.KNOWN_FEATURES.sorted(),
        hostBuildId = "current-host",
    )

    private fun localLlmProxy(handler: (String) -> Any?): ILocalLlmService = Proxy.newProxyInstance(
        ILocalLlmService::class.java.classLoader,
        arrayOf(ILocalLlmService::class.java),
    ) { _, method, _ -> handler(method.name) } as ILocalLlmService

    private fun consumerProxy(): IConsumerLocalLlmService = Proxy.newProxyInstance(
        IConsumerLocalLlmService::class.java.classLoader,
        arrayOf(IConsumerLocalLlmService::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "asBinder" -> null

            else -> throw AssertionError("Consumer delegate should not be invoked by endpoint construction: ${method.name}")
        }
    } as IConsumerLocalLlmService
}
