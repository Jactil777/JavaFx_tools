# ========================================
# DevToolBox Windows 打包脚本
# ========================================
$ProgressPreference = "SilentlyContinue"
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  DevToolBox Windows 打包工具" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan
# 1. 检查并设置 Java 17
Write-Host "[1/7] 检查 Java 环境..." -ForegroundColor Yellow
$jpackageCmd = Get-Command jpackage -ErrorAction SilentlyContinue
if (-not $jpackageCmd) {
    Write-Host "  错误: 未找到 jpackage（需要 JDK 17+）" -ForegroundColor Red
    exit 1
}
$jdk17Path = Split-Path (Split-Path $jpackageCmd.Source)
Write-Host "  检测到 JDK 17: $jdk17Path" -ForegroundColor Green
$env:JAVA_HOME = $jdk17Path
$javaExe = Join-Path $jdk17Path "bin\java.exe"
$javaVersionOutput = & $javaExe -version 2>&1
$javaVersionLine = ($javaVersionOutput | Select-String "version" | Select-Object -First 1).Line
Write-Host "  $javaVersionLine" -ForegroundColor Green
# 2. 检查 Maven
Write-Host "`n[2/7] 检查 Maven..." -ForegroundColor Yellow
$mvnVersionOutput = mvn -version 2>&1
$mvnVersionLine = ($mvnVersionOutput | Select-String "Apache Maven" | Select-Object -First 1).Line
Write-Host "  $mvnVersionLine" -ForegroundColor Green
# 3. 检查并设置 WiX Toolset
Write-Host "`n[3/7] 检查 WiX Toolset..." -ForegroundColor Yellow
$wixFound = $false
$candleCmd = Get-Command candle -ErrorAction SilentlyContinue
if ($candleCmd) {
    Write-Host "  ✓ WiX 已在 PATH 中" -ForegroundColor Green
    $wixFound = $true
} else {
    # 尝试在常见位置查找 WiX
    $possiblePaths = @(
        "C:\Program Files (x86)\WiX Toolset v3.14\bin",
        "C:\Program Files (x86)\WiX Toolset v3.11\bin",
        "C:\Program Files\WiX Toolset v3.14\bin",
        "C:\Program Files\WiX Toolset v3.11\bin"
    )
    foreach ($path in $possiblePaths) {
        $candlePath = Join-Path $path "candle.exe"
        if (Test-Path $candlePath) {
            Write-Host "  检测到 WiX: $path" -ForegroundColor Green
            $env:PATH = "$path;$env:PATH"
            Write-Host "  已临时添加到 PATH" -ForegroundColor Gray
            $wixFound = $true
            break
        }
    }
}
if (-not $wixFound) {
    Write-Host "  错误: 未找到 WiX Toolset" -ForegroundColor Red
    Write-Host "  请从以下地址下载并安装:" -ForegroundColor Yellow
    Write-Host "  https://github.com/wixtoolset/wix3/releases" -ForegroundColor Cyan
    exit 1
}
# 4. 清理旧文件
Write-Host "`n[4/7] 清理旧文件..." -ForegroundColor Yellow
if (Test-Path "dist") {
    Remove-Item -Recurse -Force "dist"
    Write-Host "  已删除 dist 目录" -ForegroundColor Gray
}
if (Test-Path "target") {
    Remove-Item -Recurse -Force "target"
    Write-Host "  已删除 target 目录" -ForegroundColor Gray
}
# 5. Maven 编译打包
Write-Host "`n[5/7] Maven 编译打包..." -ForegroundColor Yellow
Write-Host "  执行: mvn clean package -DskipTests" -ForegroundColor Gray
Write-Host "  （首次编译会下载依赖，请耐心等待...）" -ForegroundColor Gray
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
mvn clean package -DskipTests 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n  编译失败，重新执行查看详情..." -ForegroundColor Red
    mvn clean package -DskipTests
    exit 1
}
Write-Host "  编译成功" -ForegroundColor Green
# 6. 收集依赖
Write-Host "`n[6/7] 收集依赖 jar..." -ForegroundColor Yellow
mvn dependency:copy-dependencies -DoutputDirectory=target/libs 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "  依赖收集失败" -ForegroundColor Red
    exit 1
}
Write-Host "  依赖收集完成" -ForegroundColor Green
# 7. 生成 exe
Write-Host "`n[7/7] 生成 Windows 安装包..." -ForegroundColor Yellow
$jarPath = "target\DevToolBox-1.0.0.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "  错误: 未找到 $jarPath" -ForegroundColor Red
    exit 1
}
Write-Host "  执行 jpackage（可能需要 2-3 分钟）..." -ForegroundColor Gray
jpackage `
    --type exe `
    --name "DevToolBox" `
    --app-version "1.0.0" `
    --description "DevToolBox - 后端开发者桌面工具箱" `
    --vendor "DevToolBox" `
    --dest dist `
    --input target `
    --main-jar DevToolBox-1.0.0.jar `
    --main-class com.devtool.Launcher `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Xms256m" `
    --java-options "-Xmx1024m" `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "`n  jpackage 执行失败，重新执行查看详情..." -ForegroundColor Red
    jpackage `
        --type exe `
        --name "DevToolBox" `
        --app-version "1.0.0" `
        --description "DevToolBox - 后端开发者桌面工具箱" `
        --vendor "DevToolBox" `
        --dest dist `
        --input target `
        --main-jar DevToolBox-1.0.0.jar `
        --main-class com.devtool.Launcher `
        --java-options "-Dfile.encoding=UTF-8" `
        --java-options "-Xms256m" `
        --java-options "-Xmx1024m" `
        --win-dir-chooser `
        --win-menu `
        --win-shortcut
    exit 1
}
Write-Host "`n========================================"  -ForegroundColor Green
Write-Host "  打包完成！" -ForegroundColor Green
Write-Host "========================================`n" -ForegroundColor Green
Write-Host "  安装包位置: dist\DevToolBox-1.0.0.exe" -ForegroundColor Cyan
# 显示文件大小
if (Test-Path "dist\DevToolBox-1.0.0.exe") {
    $size = (Get-Item "dist\DevToolBox-1.0.0.exe").Length / 1MB
    Write-Host "  大小: $([Math]::Round($size, 1)) MB" -ForegroundColor Gray
}
Write-Host "`n  现在可以分享给用户安装使用！`n" -ForegroundColor Yellow