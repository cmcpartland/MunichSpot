# Ktor pulls in optional JVM-only classes that don't exist on Android.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
