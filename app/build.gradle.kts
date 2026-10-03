import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

fun getLocalProperty(key: String): String {
    return localProperties.getProperty(key) ?: ""
}

android {
    namespace = "com.giangnam.chat_app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.giangnam.chat_app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exposing API Keys to BuildConfig
        buildConfigField("String", "GEMINI_API_KEY", "\"${getLocalProperty("GEMINI_API_KEY")}\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${getLocalProperty("ONESIGNAL_APP_ID")}\"")
        buildConfigField("String", "ONESIGNAL_REST_API_KEY", "\"${getLocalProperty("ONESIGNAL_REST_API_KEY")}\"")
        buildConfigField("String", "GIPHY_API_KEY", "\"${getLocalProperty("GIPHY_API_KEY")}\"")
        buildConfigField("String", "IMGBB_API_KEY", "\"${getLocalProperty("IMGBB_API_KEY")}\"")
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${getLocalProperty("CLOUDINARY_CLOUD_NAME")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${getLocalProperty("CLOUDINARY_UPLOAD_PRESET")}\"")
        buildConfigField("String", "ZEGO_APP_ID", "\"${getLocalProperty("ZEGO_APP_ID")}\"")
        buildConfigField("String", "ZEGO_APP_SIGN", "\"${getLocalProperty("ZEGO_APP_SIGN")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    viewBinding {
        enable = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.firebase.auth)
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation(libs.firebase.storage)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("com.firebaseui:firebase-ui-database:10.0.0-beta04")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.onesignal:OneSignal:5.1.11")
    implementation("com.google.firebase:firebase-storage-ktx:20.3.0")
    implementation("com.github.ZEGOCLOUD:zego_uikit_prebuilt_call_android:3.+")
    implementation(libs.image.cropper)
    
    // Notifications & UI Badges
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.github.tapadoo:alerter:7.2.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Location & Maps
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
}