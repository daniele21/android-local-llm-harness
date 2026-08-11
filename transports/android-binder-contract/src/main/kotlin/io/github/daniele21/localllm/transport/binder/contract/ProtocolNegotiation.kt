package io.github.daniele21.localllm.transport.binder.contract

fun negotiateProtocol(
    host: ProtocolInfoParcel,
    client: ClientHelloParcel,
): NegotiatedProtocol {
    validateProtocolInfo(host)
    validateClientHello(client)
    validateProtocolMajor(host, client)

    val negotiatedMinor = negotiateMinor(host, client)
    validateRequiredFeatures(host, client)

    return NegotiatedProtocol(
        minor = negotiatedMinor,
        enabledFeatures = host.supportedFeatures.toSet().intersect(BinderProtocolV1.KNOWN_FEATURES),
    )
}

fun validateProtocolInfo(value: ProtocolInfoParcel) {
    requireWire(value.protocolMajor > 0, "Protocol major must be positive")
    requireWire(value.protocolMinor >= 0, "Protocol minor must be non-negative")
    requireWire(value.minSupportedMinor in 0..value.protocolMinor, "Invalid host minor range")
    validateFeatureList(value.supportedFeatures)
    validateIdentifier(
        value.hostBuildId,
        BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS,
        "host build ID",
    )
}

fun validateClientHello(value: ClientHelloParcel) {
    requireWire(value.protocolMajor > 0, "Protocol major must be positive")
    requireWire(value.protocolMinor >= 0, "Protocol minor must be non-negative")
    requireWire(value.minSupportedMinor in 0..value.protocolMinor, "Invalid client minor range")
    validateFeatureList(value.requiredFeatures)
    validateIdentifier(
        value.clientBuildId,
        BinderProtocolV1.MAX_CLIENT_BUILD_ID_CHARACTERS,
        "client build ID",
    )
}

private fun validateFeatureList(features: List<String>) {
    requireWire(features.size <= 64, "Too many protocol features")
    requireWire(features.toSet().size == features.size, "Protocol features must be unique")
    features.forEach { validateIdentifier(it, 64, "protocol feature") }
}

private fun validateProtocolMajor(
    host: ProtocolInfoParcel,
    client: ClientHelloParcel,
) {
    requireWire(
        host.protocolMajor == client.protocolMajor,
        "Protocol major versions are incompatible",
        WireErrorCodes.PROTOCOL_INCOMPATIBLE,
    )
}

private fun negotiateMinor(
    host: ProtocolInfoParcel,
    client: ClientHelloParcel,
): Int {
    val lowerBound = maxOf(host.minSupportedMinor, client.minSupportedMinor)
    val upperBound = minOf(host.protocolMinor, client.protocolMinor)
    requireWire(
        lowerBound <= upperBound,
        "Protocol minor ranges do not overlap",
        WireErrorCodes.PROTOCOL_INCOMPATIBLE,
    )
    return upperBound
}

private fun validateRequiredFeatures(
    host: ProtocolInfoParcel,
    client: ClientHelloParcel,
) {
    val supported = host.supportedFeatures.toSet()
    val unavailable = client.requiredFeatures.filterNot(supported::contains)
    requireWire(
        unavailable.isEmpty(),
        "Required protocol feature is unavailable",
        WireErrorCodes.FEATURE_UNAVAILABLE,
    )
}
