plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.ad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ad"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation ("com.github.skydoves:colorpickerview:2.2.4")
    implementation ("androidx.activity:activity:1.8.0") // Or latest version
    implementation ("androidx.fragment:fragment:1.6.2") // Ensure Fragment compatibility
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}