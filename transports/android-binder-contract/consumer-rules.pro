# Cross-process AIDL interfaces and Parcelable wire DTOs form the Binder ABI
# shared by independently built host and consumer APKs. Preserve generated
# serialization/stub/proxy behavior when a consuming application enables R8.
#
# This policy is owned and shipped by the published Binder contract AAR; a
# consumer application must not need Harness-specific optimizer rules.
-keep class io.github.daniele21.localllm.transport.binder.contract.*Parcel { *; }
-keep class io.github.daniele21.localllm.transport.binder.contract.*Parcel$* { *; }
-keep interface io.github.daniele21.localllm.transport.binder.contract.I* { *; }
-keep class io.github.daniele21.localllm.transport.binder.contract.I*$* { *; }
