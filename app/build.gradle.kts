import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    kotlin("plugin.serialization") version "2.0.0"
}

android {
    namespace = "com.example.projeto_cmen_gestor_receitas_lista_compras"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    viewBinding { enable = true }

    defaultConfig {
        applicationId = "com.example.projeto_cmen_gestor_receitas_lista_compras"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val propertiesFile = project.rootProject.file("local.properties")
        if (propertiesFile.exists()) {
            properties.load(propertiesFile.inputStream())
        } else {
            println("AVISO: Ficheiro local.properties não encontrado. As chaves SUPABASE serão vazias.")
        }

        buildConfigField("String", "SUPABASE_URL",
            "\"${properties.getProperty("supabase.url") ?: ""}\"")
        buildConfigField("String", "SUPABASE_KEY",
            "\"${properties.getProperty("supabase.key") ?: ""}\"")

        buildConfigField("String", "CLOUD_API_KEY",
            "\"${properties.getProperty("google.cloud.key") ?: ""}\"")
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
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform("io.ktor:ktor-bom:2.3.12"))
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-encoding")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform(libs.supabase.bom))

    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")

    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}