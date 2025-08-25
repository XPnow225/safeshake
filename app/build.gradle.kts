
plugins{ id("com.android.application"); id("org.jetbrains.kotlin.android") }
android{
  namespace="com.safeshake"; compileSdk=34
  defaultConfig{ applicationId="com.safeshake"; minSdk=24; targetSdk=34; versionCode=2; versionName="1.0.0" }
  buildTypes{
    release{ isMinifyEnabled=false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") }
    debug{ applicationIdSuffix=".debug"; versionNameSuffix="-debug" }
  }
  compileOptions{ sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
  kotlinOptions{ jvmTarget="17" }
}
dependencies{
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
  implementation("androidx.activity:activity-ktx:1.9.1")
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
