# agentterm — original code, MIT licensed (see LICENSE)
# Optional optimization rules; nothing required for the MVP build.
-dontwarn org.bouncycastle.**
-dontwarn com.hierynomus.**
-keep class com.hierynomus.** { *; }
-keep class net.schmizz.sshj.** { *; }