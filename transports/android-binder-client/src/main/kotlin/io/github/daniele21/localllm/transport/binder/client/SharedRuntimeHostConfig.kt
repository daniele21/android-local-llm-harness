package io.github.daniele21.localllm.transport.binder.client

/**
 * Explicit, trusted host-service configuration for the shared-runtime client.
 *
 * The client never scans installed packages or resolves an implicit intent. Consumers supply the
 * exact package and service class that are allowed to host the shared runtime.
 */
class SharedRuntimeHostConfig private constructor(val packageName: String, val serviceClassName: String) {
    companion object {
        fun create(packageName: String, serviceClassName: String): SharedRuntimeHostConfig {
            val normalizedPackage = packageName.requireCanonical("packageName")
            val normalizedService = serviceClassName.requireCanonical("serviceClassName")
            require('/' !in normalizedPackage) { "packageName must not contain '/'" }
            require('/' !in normalizedService) { "serviceClassName must not contain '/'" }

            val qualifiedService = when {
                normalizedService.startsWith('.') -> normalizedPackage + normalizedService
                '.' !in normalizedService -> "$normalizedPackage.$normalizedService"
                else -> normalizedService
            }
            return SharedRuntimeHostConfig(normalizedPackage, qualifiedService)
        }

        private fun String.requireCanonical(fieldName: String): String {
            require(isNotBlank()) { "$fieldName must not be blank" }
            require(this == trim()) { "$fieldName must not contain surrounding whitespace" }
            require(none(Char::isWhitespace)) { "$fieldName must not contain whitespace" }
            return this
        }
    }

    override fun equals(other: Any?): Boolean = other is SharedRuntimeHostConfig &&
        packageName == other.packageName &&
        serviceClassName == other.serviceClassName

    override fun hashCode(): Int = 31 * packageName.hashCode() + serviceClassName.hashCode()

    override fun toString(): String = "SharedRuntimeHostConfig(packageName=$packageName, serviceClassName=$serviceClassName)"
}
