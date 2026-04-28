param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = 'Stop'

$gradleVersion = '8.10.2'
$cacheRoot = Join-Path $env:LOCALAPPDATA 'WarpMobile\gradle'
$gradleHome = Join-Path $cacheRoot "gradle-$gradleVersion"
$gradleZip = Join-Path $cacheRoot "gradle-$gradleVersion-bin.zip"
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'

if (-not $env:JAVA_HOME -and (Test-Path 'D:\Android Studio\jbr')) {
    $env:JAVA_HOME = 'D:\Android Studio\jbr'
}

if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}

if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    if (-not (Test-Path $gradleZip)) {
        $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
        Invoke-WebRequest -Uri $url -OutFile $gradleZip
    }
    Expand-Archive -LiteralPath $gradleZip -DestinationPath $cacheRoot -Force
}

if ($GradleArgs.Count -eq 0) {
    $GradleArgs = @('tasks')
}

& $gradleExe -p $PSScriptRoot @GradleArgs
exit $LASTEXITCODE
