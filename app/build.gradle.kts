plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.foodtrack.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.foodtrack.app"
        // Supports Android 4.4 (KitKat) and up, per project requirement.
        minSdk = 19
        // Kept current so the app remains publishable/visible on Google Play.
        // Bump this forward as Play's requirement advances (check before each release).
        targetSdk = 28
        versionCode = 3
        versionName = "0.1.2"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // NOTE ON VERSIONS: pinned deliberately to a slightly older, known-good
    // stack. androidx.appcompat and com.google.android.material both raised
    // their floor to minSdk 21 in newer releases (appcompat 1.7.0+, material
    // ~1.12+), which breaks the manifest merge against our minSdk 19 target.
    // These versions are the last ones confirmed to still support API 19 —
    // do not bump appcompat/material past these without re-checking their
    // release notes for a minSdk floor change.
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.2.1")

    // Lifecycle / coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")

    // Room (SQLite ORM) - works fine down to API 14, and supports pre-populating
    // the DB from a bundled asset via createFromAsset(), which is what we use
    // to ship the translated USDA-derived food database inside the APK.
    // Room itself hasn't raised its minSdk floor, so this stays current.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hebrew (Jewish) calendar conversion — KosherJava Zmanim library.
    // https://github.com/KosherJava/zmanim (published to Maven Central).
    // Pure Java, java.util.Date/Calendar based — no java.time usage in the
    // code paths we call, so no core library desugaring is required and it
    // stays compatible with our minSdk 19 floor.
    implementation("com.kosherjava:zmanim:2.5.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
