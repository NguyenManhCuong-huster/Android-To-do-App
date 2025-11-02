plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

android {
    namespace = "com.project3.todoapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.project3.todoapp"
        minSdk = 29
        targetSdk = 36
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

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ✅ Room Database
    implementation(libs.androidx.room.runtime)
    ksp (libs.androidx.room.compiler)
    implementation (libs.room.ktx)

    // ViewModel và lifecycle cơ bản
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // (Tùy chọn) Dành cho LiveData nếu bạn dùng
    implementation (libs.androidx.lifecycle.livedata.ktx)

    // (Tùy chọn) Dành cho runtime components
    implementation (libs.androidx.lifecycle.runtime.ktx)

}