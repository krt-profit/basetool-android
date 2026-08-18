# App ProGuard/R8 rules.
# Compose, AndroidX and Kotlin ship their own consumer rules; keep this file
# for app-specific keeps as the feature modules land.

# Nimbus JOSE ships a shaded Gson whose builder methods are annotated with Error Prone's
# @CanIgnoreReturnValue. The annotation is compile-time only and never on the runtime classpath, so
# R8 reports it as missing the moment the release build reaches the DPoP proof factory. Warning
# rather than keeping: there is nothing to keep — the class genuinely does not exist at runtime and
# nothing reads it.
-dontwarn com.google.errorprone.annotations.**
