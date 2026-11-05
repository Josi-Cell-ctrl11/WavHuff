plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.josias.wavhuff"
    compileSdk = 35
    // Le NDK compile les sources C de LAME et le pont JNI.
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "com.josias.wavhuff"
        minSdk = 26
        targetSdk = 35
        // Entier croissant utilisé par Android pour identifier les mises à jour.
        versionCode = 3
        versionName = "1.0.2"
        // Bibliothèques pour les téléphones ARM et les émulateurs x86_64.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        // Alignement compatible avec les appareils Android à pages mémoire de 16 Kio.
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
