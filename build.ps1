# Compila o projeto. Tudo (SDK, Gradle e cache) vive no disco D: de proposito.
$env:JAVA_HOME        = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME     = "D:\Android\Sdk"
$env:ANDROID_SDK_ROOT = "D:\Android\Sdk"
$env:GRADLE_USER_HOME = "D:\Android\.gradle"

$gradle = "D:\Android\Gradle\gradle-8.10.2\bin\gradle.bat"
$task   = if ($args.Count -gt 0) { $args } else { @(":diag:assembleDebug") }

& $gradle --project-dir "D:\Alpha3\geely" @task
