# KidBox — regole R8/ProGuard per il build release.
#
# La maggior parte delle librerie (Room, Hilt, Firebase, WorkManager, OkHttp,
# ML Kit, CameraX) porta le proprie consumer-rules e non necessita di nulla qui.
# Servono regole solo dove usiamo reflection direttamente.

# --- Gson (OtpSecureStore serializza/deserializza OtpConfig via reflection) ---
# Gson legge i nomi dei campi a runtime: senza keep verrebbero offuscati.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Modello serializzato con Gson: mantieni i campi con il nome originale.
-keep class it.vittorioscocca.kidbox.data.passwords.otp.OtpConfig { *; }

# --- PDFBox (tom-roush): reflection interna su font/encoding, silenzia i warning ---
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**
-dontwarn org.bouncycastle.**
