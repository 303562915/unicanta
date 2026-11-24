plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

/**
 * Release APK 的根入口。
 *
 * 这个工程是多模块的，Android 应用在 `:androidApp`，所以根项目本身没有
 * `assembleRelease` 任务。F-Droid 的 recipe 只能跑 `gradle assembleRelease`
 * （`gradle: - yes` 就是这一条，没法在 metadata 里指定模块路径），因此这里
 * 加一个委托任务把调用转到 `:androidApp:assembleRelease`。
 *
 * 产物位置不变：`androidApp/build/outputs/apk/release/*.apk`，
 * F-Droid 侧由 recipe 的 `output:` 指向它。
 */
tasks.register("assembleRelease") {
    group = "build"
    description = "构建 :androidApp 的 release APK（:androidApp:assembleRelease 的委托）"
    dependsOn(":androidApp:assembleRelease")
}
