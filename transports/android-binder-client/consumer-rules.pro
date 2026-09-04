# Shared-runtime Binder interfaces cross an Android IPC boundary through generated stubs/proxies.
# Keep the generated Binder contract and Parcelable creators when a consuming app enables shrinking.
-keep class io.github.daniele21.localllm.transport.binder.contract.I** { *; }
-keepclassmembers class io.github.daniele21.localllm.transport.binder.contract.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
