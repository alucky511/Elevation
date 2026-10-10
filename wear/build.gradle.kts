plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.elevation.wear"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.elevation"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
}
