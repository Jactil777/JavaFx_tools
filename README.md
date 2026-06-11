# DevToolBox · 后端开发者桌面工具箱

> 专为开发者打造的本地桌面工具箱，纯本地运算，无网络请求，无数据上传。  
> 技术栈：Java 17 + JavaFX 17 + Maven

---

## 目录

- [项目概览](#项目概览)
- [环境要求](#环境要求)
- [项目结构](#项目结构)
- [启动方式](#启动方式)
- [基础架构说明](#基础架构说明)
- [新增业务功能页面（开发指引）](#新增业务功能页面开发指引)
- [功能模块状态](#功能模块状态)
- [版本记录](#版本记录)

---

## 项目概览

| 项目 | 说明 |
|------|------|
| 应用名称 | DevToolBox 开发者工具箱 |
| 当前版本 | v1.0.3 |
| 开发语言 | Java 17 |
| UI 框架 | JavaFX 17.0.8 |
| 构建工具 | Maven 3.x |
| 运行平台 | Windows 10 / 11 |
| 网络依赖 | 无（纯本地运算） |

---

## 环境要求

| 工具 | 版本要求 | 备注 |
|------|----------|------|
| JDK | **17**（必须） | 推荐 Microsoft OpenJDK 17 / Liberica JDK 17 |
| Maven | 3.6+ | IDEA 内置或系统安装均可 |
| IDE | IntelliJ IDEA | 推荐，已预置运行配置 |

> ⚠️ **重要**：本项目使用的 JDK 17（Microsoft OpenJDK）**不内置 JavaFX**，  
> JavaFX 运行时完全由 Maven 依赖提供（`classifier=win` 含原生 DLL）。  
> 不要用 JDK 8 运行，不要用不带 `classifier=win` 的 JavaFX 依赖。

---

## 项目结构

```
JavaFx_tools/
├── pom.xml                          # Maven 依赖 & 插件配置
├── README.md                        # 本文件
└── src/main/
    ├── java/com/devtool/
    │   ├── Launcher.java            # ★ 程序启动入口（通过此类运行，非 Main）
    │   ├── Main.java                # JavaFX Application 实现（窗口初始化）
    │   ├── config/
    │   │   └── AppConfig.java       # 全局常量（窗口尺寸、版本号、FXML 路径）
    │   ├── controller/
    │   │   ├── BaseController.java  # 页面控制器基类（定义生命周期方法）
    │   │   ├── MainController.java  # 主框架控制器（导航栏 + 页面切换 + 缓存）
    │   │   ├── EmptyPageController.java  # 首页欢迎页控制器
    │   │   └── JsonPageController.java   # JSON 工具箱控制器
    │   ├── util/
    │   │   ├── DialogUtil.java      # 通用弹窗工具（info / error / warn / confirm）
    │   │   ├── ExceptionHandler.java # 全局未捕获异常处理（防闪退）
    │   │   ├── JsonUtil.java        # JSON 工具类（格式化、压缩、对比、生成代码）
    │   │   └── SystemUtil.java      # 系统工具（剪贴板、打开文件/URL 等）
    │   └── view/
    │       └── PageEnum.java        # 页面枚举（统一管理所有功能页，新增只需加一行）
    └── resources/
        ├── fxml/
        │   ├── main.fxml            # 主框架布局（顶栏 + 左侧导航 + 内容区 + 状态栏）
        │   ├── page_empty.fxml      # 首页欢迎页布局
        │   └── page_json.fxml       # JSON 工具箱页面布局
        ├── css/
        │   └── global.css           # 全局深色主题样式
        ├── images/
        │   └── app_icon.png         # 窗口图标（可替换为自己的 PNG）
        └── logback.xml              # 日志配置（控制台 + 文件滚动，保留 7 天）
```

---

## 启动方式

### 方式一：Maven 命令行（推荐，任何情况都可用）

```powershell
# 1. 设置 JAVA_HOME 指向 JDK 17
$env:JAVA_HOME = "D:\devtools\jdk17"   # 替换为你本机 JDK17 路径

# 2. 启动应用
cd E:\person_code\JavaFx_tools
mvn exec:java
```

### 方式二：IDEA 内置 Maven 面板

1. 打开右侧 **Maven** 面板
2. 展开 `Plugins` → `exec` → 双击 **`exec:java`**

### 方式三：IDEA Run 菜单（已预置配置）

1. 顶部菜单 **Run** → **DevToolBox Run**
2. 该配置文件位于 `.idea/runConfigurations/DevToolBox_Run.xml`，IDEA 自动识别

> ⛔ **不要**直接右键 `Main.java` 或 `Launcher.java` → Run  
> IDEA 的普通 Application 运行方式不会正确处理 JavaFX 的 classpath，  
> 会报 `缺少 JavaFX 运行时组件` 或 `Module javafx.controls not found`。

---

## 打包 Windows 安装包

### 快速开始

**双击运行** 项目根目录的 **`一键打包.bat`** 文件，或在 PowerShell 中执行：

```powershell
cd E:\person_code\JavaFx_tools
.\一键打包.bat
# 或
.\build-exe.ps1
```

打包完成后，安装包位于 `dist\DevToolBox-1.0.0.exe`（约 60 MB，含完整 JRE）

### 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| **JDK 17+** | 必需 | 需包含 jpackage 工具 |
| **Maven 3.6+** | 必需 | 用于编译打包 |
| **WiX Toolset 3.x** | 必需 | 用于生成 Windows 安装包 |

#### 安装 WiX Toolset

1. 下载：https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314.exe
2. 双击安装
3. 重启 PowerShell

打包脚本会自动检测 WiX 并添加到 PATH。

### 打包流程

```
开始打包
  ↓
[1/7] 检查 Java 17 环境
  ↓
[2/7] 检查 Maven
  ↓
[3/7] 检查 WiX Toolset（自动配置 PATH）
  ↓
[4/7] 清理旧文件（dist、target）
  ↓
[5/7] Maven 编译打包（30-60秒）
  ↓
[6/7] 收集依赖 jar
  ↓
[7/7] 生成 Windows 安装包（1-2分钟）
  ↓
完成！dist\DevToolBox-1.0.0.exe
```

**首次打包约 2-3 分钟，后续打包约 1-2 分钟。**

### 生成的安装包特性

✅ **自包含 JRE** - 用户无需安装 Java  
✅ **标准安装向导** - Windows 原生安装体验  
✅ **开始菜单** - 自动添加快捷方式  
✅ **桌面快捷方式** - 可选创建  
✅ **完整卸载** - 控制面板标准卸载  
✅ **UTF-8 编码** - 完美支持中文，无乱码

### 疑难解答

**Q: 双击 .ps1 文件没反应？**  
A: 请双击 `一键打包.bat` 文件，或右键 `.ps1` 文件 → **使用 PowerShell 运行**

**Q: 提示"无法加载文件"？**  
A: PowerShell 执行策略限制，管理员运行 PowerShell：
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**Q: 找不到 WiX 工具？**  
A: 安装 WiX Toolset 3.14 后重启 PowerShell，脚本会自动检测

**Q: 打包后的 exe 太大？**  
A: 正常现象，因为包含了完整的 JRE（约 50-60 MB），用户无需安装 Java

---

## 基础架构说明

### 页面切换机制

```
用户点击左侧导航
        ↓
MainController.switchPage(PageEnum)
        ↓
从缓存(pageCache)中取 / 首次用 FXMLLoader 加载
        ↓
设置到 contentPane 中心区域
        ↓
调用 BaseController.onPageInit()  ← 每次切换都触发
```

- **页面缓存**：每个页面首次加载后缓存到 `Map<PageEnum, Pane>`，切换时不重复解析 FXML
- **生命周期**：`onPageInit()` 每次切换到该页时触发；`onPageDestroy()` 离开时触发

### 为什么用 `Launcher` 而不是 `Main` 作为入口？

JavaFX 17 是模块化设计，JVM 在加载 `Application` 子类之前会先做模块检查。  
当 JavaFX 通过 Maven classpath 而非 `--module-path` 提供时，这个检查会失败。  
`Launcher`（普通类，非 `Application` 子类）作为入口，绕过了这个时序问题。  
这是 OpenJFX 官方推荐的标准做法。

### 通用工具类速查

| 类 | 常用方法 |
|----|---------|
| `DialogUtil` | `info(title, msg)` / `error(title, msg)` / `warn(title, msg)` / `confirm(title, msg)→boolean` |
| `SystemUtil` | `copyToClipboard(text)` / `copyToClipboardSilent(text)` / `openFile(file)` / `openUrl(url)` |
| `JsonUtil` | `format(json)` / `compress(json)` / `escape(json)` / `unescape(json)` / `compare(json1, json2)` / `generateJavaClass(json, className)` |

---

## 新增业务功能页面（开发指引）

以新增「JSON 工具」页为例，只需 **3 步**：

### 第 1 步：新建 FXML 布局

新建 `src/main/resources/fxml/page_json.fxml`，参照 `page_empty.fxml` 的结构：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?import javafx.scene.layout.VBox?>

<VBox xmlns="http://javafx.com/javafx/17"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.devtool.controller.JsonPageController"
      styleClass="page-container">
    <!-- 在这里写页面 UI -->
</VBox>
```

### 第 2 步：新建 Controller

新建 `src/main/java/com/devtool/controller/JsonPageController.java`：

```java
package com.devtool.controller;

public class JsonPageController extends BaseController {

    @Override
    public void onPageInit() {
        // 每次切换到此页时执行（刷新数据、重置状态等）
    }
}
```

### 第 3 步：在 PageEnum 中注册

打开 `src/main/java/com/devtool/view/PageEnum.java`，取消注释对应行：

```java
// 改动前（注释状态）：
// JSON_TOOL("📋 JSON工具", "/fxml/page_json.fxml"),

// 改动后（取消注释）：
JSON_TOOL("📋 JSON工具", "/fxml/page_json.fxml"),
```

**重启应用，左侧导航栏自动出现新菜单项，点击即可跳转。**

---

## 功能模块状态

### 已完成（基础架构）

| 模块 | 状态 | 说明 |
|------|------|------|
| 主窗口框架 | ✅ 完成 | 顶栏 + 左侧导航 + 内容区 + 底部状态栏 |
| 深色主题 CSS | ✅ 完成 | 全局深色配色，统一控件样式 |
| 页面切换框架 | ✅ 完成 | 带缓存的动态 FXML 页面切换 |
| 控制器基类 | ✅ 完成 | `onPageInit` / `onPageDestroy` 生命周期 |
| 全局异常捕获 | ✅ 完成 | 未捕获异常弹窗提示，防止闪退 |
| 通用弹窗工具 | ✅ 完成 | info / error / warn / confirm，跨线程安全 |
| 剪贴板 & 系统工具 | ✅ 完成 | 复制、打开文件、打开 URL |
| 日志系统 | ✅ 完成 | logback，控制台 + 文件滚动（`logs/` 目录） |
| 首页欢迎页 | ✅ 完成 | 展示待上线功能列表 |

### 已完成（业务功能）

| 模块 | 状态 | 说明 |
|------|------|------|
| 📋 JSON 工具箱 | ✅ 完成 | 格式化、压缩、转义/反转义、JSON 对比（高亮差异行）、生成 Java/Python/Go 实体类 |
| 🔑 随机密码生成 | ✅ 完成 | 可配置长度/字符集/规则，强度评估，批量生成，一键复制 |
| 📷 二维码生成器 | ✅ 完成 | 支持文本/网址/邮箱，可调尺寸，保存 PNG，复制到剪贴板 |
| ⏰ Cron 表达式 | ✅ 完成 | 可视化配置，实时预览，下次 10 次触发时间，14 个常用模板 |

### 待开发（业务功能）

| 模块 | 状态 | 计划功能 |
|------|------|---------|
| 🔐 加密解密 | 🔲 待开发 | MD5、SHA256、AES、Base64 |
| ⏱ 时间戳工具 | 🔲 待开发 | 毫秒/秒互转、多时区、日期计算 |
| 🗄 SQL 工具 | 🔲 待开发 | SQL 格式化、关键字大写、去冗余空格 |
| 📝 文本批量处理 | 🔲 待开发 | 批量替换、去空行/空格、编码转换 |

---

## 版本记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0.0 | 2026-06-11 | 项目基座完成：主框架、导航、深色主题、通用工具类、日志、异常捕获 |
| v1.0.1 | 2026-06-11 | 完成 JSON 工具箱：格式化、压缩、转义/反转义、JSON 对比（差异高亮）、生成 Java/Python/Go 实体类 |
| v1.0.2 | 2026-06-11 | 完成随机密码生成器：可配置字符集/长度/规则，强度评估，批量生成 |
| v1.0.3 | 2026-06-11 | 完成二维码生成器（文本/网址/邮箱）、Cron 表达式生成器（可视化配置 + 触发预览）|

