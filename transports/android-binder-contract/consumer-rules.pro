# The classes in this package are the cross-process Binder wire ABI shared by
# independently built host and consumer APKs. Their generated AIDL/Parcelable
# code must keep the same observable serialization contract after whole-program
# shrinking in a consuming application.
#
# Keep this rule owned by the published Binder contract AAR. Consumer apps must
# not need Harness-specific ProGuard/R8 rules of their own.
-keep class io.github.daniele21.localllm.transport.binder.contract.** { *; }
