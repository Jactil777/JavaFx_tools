# DevToolBox

> A local-first desktop toolbox for backend developers, built with Java 17 and JavaFX.

[中文](#devtoolbox-开发者工具箱) | [English](#devtoolbox-developer-toolbox)

![Java](https://img.shields.io/badge/Java-17-blue)
![JavaFX](https://img.shields.io/badge/JavaFX-17.0.8-blueviolet)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36)
![Platform](https://img.shields.io/badge/Platform-Windows-lightgrey)
![License](https://img.shields.io/badge/License-MIT-green)

---

# DevToolBox 开发者工具箱

DevToolBox 是一个面向后端开发者和日常工程效率场景的 JavaFX 桌面工具箱。它把 JSON、SQL、文本处理、时间戳、加密解密、二维码、Cron、开发日报、翻译、AI 助手等常用能力整合到一个本地桌面应用中。

项目优先考虑本地运行、低依赖、启动简单和可维护的页面化架构。除 AI 助手、翻译助手、内置浏览器等明确需要网络的功能外，其他工具均在本机完成计算。

## 目录

- [特性](#特性)
- [功能模块](#功能模块)
- [技术栈](#技术栈)
- [快速开始](#快速开始)
- [打包 Windows 安装包](#打包-windows-安装包)
- [AI 助手配置](#ai-助手配置)
- [数据与隐私](#数据与隐私)
- [项目结构](#项目结构)
- [开发指南](#开发指南)
- [贡献指南](#贡献指南)
- [安全建议](#安全建议)
- [许可证](#许可证)
- [English](#devtoolbox-developer-toolbox)

## 特性

- 本地桌面应用：无需部署服务端，直接通过 JavaFX 运行。
- 多工具集合：覆盖开发者高频文本、数据、编码、调试和日报场景。
- AI 助手：支持火山方舟 OpenAI-compatible Chat Completions，支持会话历史和模型切换。
- 页面化架构：每个工具以 FXML + Controller + Util 的方式组织，便于扩展。
- 深色主题：统一的深色 UI 风格，适合长时间使用。
- 本地持久化：生词本、AI 配置、AI 会话等数据保存在本机。
- Windows 打包：提供 `jpackage` 脚本，可生成自包含 JRE 的安装包。

## 功能模块

| 模块 | 说明 |
| --- | --- |
| JSON 工具 | 格式化、压缩、转义、反转义、差异对比、实体类生成与转换 |
| 随机密码 | 可配置长度、字符集、强度评估、批量生成 |
| 二维码 | 支持文本、网址、邮箱，支持保存 PNG 和复制到剪贴板 |
| Cron 表达式 | 可视化配置、表达式校验、触发时间预览、常用模板 |
| 加密解密 | Hash、HMAC、AES、Base64、URL 编解码 |
| 时间戳工具 | 时间戳转换、多时区显示、日期差、里程碑时间 |
| 计算器 | 表达式计算、历史记录、结果复制、键盘快捷输入 |
| AI 助手 | 火山方舟接入、多轮会话、历史列表、模型切换、本地配置 |
| 翻译助手 | 多语言翻译、英文单词释义、生词本本地存储 |
| SQL 工具 | SQL 格式化、语法检查、参数填充、INSERT 模板、元信息提取 |
| 文本处理 | 批量替换、行操作、大小写转换、空格缩进、编码转换、统计 |
| 开发日报 | 读取本地 Git 日志，按日期生成多格式日报 |
| 调试工具 | JavaFX WebView 内置浏览器、快捷网址、深色页面注入 |

## 技术栈

| 类别 | 技术 |
| --- | --- |
| 语言 | Java 17 |
| UI | JavaFX 17.0.8 |
| 构建 | Maven |
| JSON | Jackson |
| 二维码 | ZXing |
| 日志 | SLF4J + Logback |
| AI 接口 | OpenAI-compatible Chat Completions，默认适配火山方舟 |
| 目标平台 | Windows 10 / 11 |

## 快速开始

### 环境要求

- JDK 17
- Maven 3.6+
- Windows 10 / 11

> 本项目使用 Maven 引入带 `classifier=win` 的 JavaFX 依赖。请使用 JDK 17，不要使用 JDK 8 直接运行。

### 运行

```powershell
git clone https://github.com/Jactil777/JavaFx_tools.git
cd JavaFx_tools
mvn exec:java
```

也可以在 IntelliJ IDEA 中打开项目，通过 Maven 面板运行：

```text
Plugins -> exec -> exec:java
```

### 构建

```powershell
mvn clean package -DskipTests
```

构建产物位于 `target/`。

## 打包 Windows 安装包

项目提供了一键打包脚本：

```powershell
.\一键打包.bat
```

或直接执行：

```powershell
.\build-exe.ps1
```

打包依赖：

- JDK 17+，需包含 `jpackage`
- Maven 3.6+
- WiX Toolset 3.x

安装包默认输出到 `dist/`。由于安装包包含 JRE，文件体积较大是正常现象。

## AI 助手配置

AI 助手默认适配火山方舟的 OpenAI-compatible Chat Completions 接口：

```text
https://ark.cn-beijing.volces.com/api/v3/chat/completions
```

已内置常见方舟模型 ID，例如：

- `doubao-seed-2-0-mini-260428`
- `doubao-seed-2-0-pro-260215`
- `doubao-seed-2-0-lite-260428`
- `doubao-seed-2-0-code-preview-260215`
- `doubao-seed-1-8-251228`
- `doubao-seed-character-251128`
- `glm-4-7-251222`
- `deepseek-v3-2-251201`
- `deepseek-v4-flash-260425`
- `deepseek-v4-pro-260425`

在应用中打开：

```text
AI助手 -> 模型设置
```

填写 API Key，选择或手动输入模型 ID，即可开始对话。

> 不要把 API Key 写进源码或提交到 Git。DevToolBox 会把 AI 配置保存在用户目录。

## 数据与隐私

DevToolBox 默认尽量在本地处理数据。以下数据会保存在本机：

| 数据 | 默认位置 |
| --- | --- |
| AI 配置 | `%USERPROFILE%\.devtoolbox\ai-assistant.properties` |
| AI 会话历史 | `%USERPROFILE%\.devtoolbox\ai-conversations.json` |
| 生词本 | `wordbook/*.json` |
| 日志 | `logs/` |

需要网络访问的功能：

- AI 助手：请求你配置的 AI 服务，例如火山方舟。
- 翻译助手：请求翻译与词典服务。
- 调试工具：内置浏览器访问用户输入的网址。

请不要在未确认的第三方服务中输入敏感数据、生产密钥或隐私内容。

## 项目结构

```text
JavaFx_tools/
├── pom.xml
├── build-exe.ps1
├── 一键打包.bat
├── wordbook/
└── src/main/
    ├── java/com/devtool/
    │   ├── Launcher.java
    │   ├── Main.java
    │   ├── config/
    │   ├── controller/
    │   ├── util/
    │   └── view/
    └── resources/
        ├── css/
        ├── fxml/
        ├── images/
        └── logback.xml
```

核心设计：

- `PageEnum`：统一注册所有工具页面。
- `MainController`：负责左侧导航、页面切换和页面缓存。
- `BaseController`：提供 `onPageInit()` / `onPageDestroy()` 生命周期。
- `controller`：处理 JavaFX 交互。
- `util`：承载可复用的业务逻辑。
- `resources/fxml`：页面布局。
- `resources/css/global.css`：全局主题样式。

## 开发指南

新增一个工具页通常需要四步：

1. 在 `src/main/resources/fxml/` 新增页面 FXML。
2. 在 `src/main/java/com/devtool/controller/` 新增 Controller，并继承 `BaseController`。
3. 如有通用逻辑，在 `src/main/java/com/devtool/util/` 新增 Util。
4. 在 `PageEnum` 中注册页面，左侧导航会自动出现入口。

建议：

- UI 样式优先复用 `global.css` 中已有类。
- 耗时任务应放到后台线程，避免阻塞 JavaFX UI 线程。
- API Key、Token、个人数据不要写入仓库。
- 涉及公共逻辑时优先放入 `util`，避免 Controller 过重。

## 贡献指南

欢迎提交 Issue、建议和 Pull Request。

推荐流程：

1. Fork 本仓库。
2. 创建特性分支。
3. 保持改动聚焦，避免混入无关格式化。
4. 提交前运行：

```powershell
mvn -q -DskipTests package
```

5. 在 PR 中说明改动内容、测试方式和可能影响。

## 安全建议

- 不要提交 API Key、Token、Cookie 或任何生产密钥。
- 如果密钥曾经出现在聊天、截图或提交记录中，请及时轮换。
- AI 助手会把输入内容发送到配置的模型服务，请自行确认服务商的数据策略。
- 打包发布前建议检查 `logs/`、`wordbook/` 和用户配置目录，避免夹带个人数据。

## 路线图

- 增加更多 AI Provider 预设。
- 为核心 Util 补充单元测试。
- 为 README 增加应用截图。
- 将版本记录迁移到独立 `CHANGELOG.md`。
- 增强跨平台打包支持。

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

## 联系方式

- Author: Jactil
- Email: `jactil777@gmail.com`

---

# DevToolBox Developer Toolbox

DevToolBox is a JavaFX desktop toolbox for backend developers and daily engineering workflows. It brings together JSON, SQL, text processing, timestamp conversion, encryption, QR code generation, Cron helpers, Git daily reports, translation, and an AI assistant in a single local desktop application.

The project is designed to be local-first, easy to run, and easy to extend. Most tools run completely on your machine. Features such as the AI assistant, translator, and embedded browser access external services only when you use them.

## Contents

- [Highlights](#highlights)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [Windows Packaging](#windows-packaging)
- [AI Assistant Configuration](#ai-assistant-configuration)
- [Data and Privacy](#data-and-privacy)
- [Project Structure](#project-structure)
- [Development Guide](#development-guide)
- [Contributing](#contributing)
- [Security](#security)
- [License](#license)
- [Contact](#contact)

## Highlights

- Local desktop app: no backend service required.
- Developer-focused tools: common utilities for data, text, encoding, debugging, and reporting.
- AI assistant: Volcengine Ark-compatible Chat Completions, conversation history, and model switching.
- Page-based architecture: each tool is organized with FXML, Controller, and Util classes.
- Dark theme: consistent UI for long working sessions.
- Local persistence: wordbooks, AI configuration, and AI conversation history are stored locally.
- Windows packaging: `jpackage` scripts generate a self-contained installer.

## Features

| Tool | Description |
| --- | --- |
| JSON Tools | Format, minify, escape, unescape, diff, and generate entity classes |
| Password Generator | Configurable length, character sets, strength score, and batch generation |
| QR Code Generator | Generate QR codes for text, URLs, and email content |
| Cron Helper | Visual editing, validation, next trigger preview, and templates |
| Crypto Tools | Hash, HMAC, AES, Base64, and URL encoding/decoding |
| Timestamp Tools | Timestamp conversion, time zones, date diff, and milestones |
| Calculator | Expression calculation, history, copy actions, and keyboard shortcuts |
| AI Assistant | Ark integration, multi-turn chat, history list, model switching |
| Translator | Multi-language translation, English word definitions, local wordbooks |
| SQL Tools | Formatting, validation, parameter filling, INSERT templates, metadata extraction |
| Text Tools | Replace, line operations, case conversion, whitespace, encoding, and stats |
| Git Report | Generate daily reports from local Git logs |
| Debug Browser | Embedded JavaFX WebView browser with shortcuts and dark-page injection |

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| UI | JavaFX 17.0.8 |
| Build | Maven |
| JSON | Jackson |
| QR Code | ZXing |
| Logging | SLF4J + Logback |
| AI API | OpenAI-compatible Chat Completions, default preset for Volcengine Ark |
| Target Platform | Windows 10 / 11 |

## Quick Start

### Requirements

- JDK 17
- Maven 3.6+
- Windows 10 / 11

This project uses Maven-managed JavaFX dependencies with the Windows classifier. Use JDK 17; do not run it with JDK 8.

### Run

```powershell
git clone https://github.com/Jactil777/JavaFx_tools.git
cd JavaFx_tools
mvn exec:java
```

You can also run it from IntelliJ IDEA:

```text
Plugins -> exec -> exec:java
```

### Build

```powershell
mvn clean package -DskipTests
```

Build outputs are generated under `target/`.

## Windows Packaging

Use the bundled script:

```powershell
.\一键打包.bat
```

Or run the PowerShell script directly:

```powershell
.\build-exe.ps1
```

Packaging requirements:

- JDK 17+ with `jpackage`
- Maven 3.6+
- WiX Toolset 3.x

The installer is generated under `dist/`. It includes a JRE, so the file size is expected to be relatively large.

## AI Assistant Configuration

The AI assistant defaults to Volcengine Ark's OpenAI-compatible Chat Completions endpoint:

```text
https://ark.cn-beijing.volces.com/api/v3/chat/completions
```

Built-in Ark model IDs include:

- `doubao-seed-2-0-mini-260428`
- `doubao-seed-2-0-pro-260215`
- `doubao-seed-2-0-lite-260428`
- `doubao-seed-2-0-code-preview-260215`
- `doubao-seed-1-8-251228`
- `doubao-seed-character-251128`
- `glm-4-7-251222`
- `deepseek-v3-2-251201`
- `deepseek-v4-flash-260425`
- `deepseek-v4-pro-260425`

Open the following panel in the app:

```text
AI Assistant -> Model Settings
```

Enter your API key, select or type a model ID, and start chatting.

Do not hardcode API keys in source files or commit them to Git. DevToolBox stores AI settings in your local user directory.

## Data and Privacy

DevToolBox keeps most data local by default.

| Data | Default Location |
| --- | --- |
| AI settings | `%USERPROFILE%\.devtoolbox\ai-assistant.properties` |
| AI conversations | `%USERPROFILE%\.devtoolbox\ai-conversations.json` |
| Wordbooks | `wordbook/*.json` |
| Logs | `logs/` |

Network-enabled features:

- AI Assistant: sends requests to your configured AI provider, such as Volcengine Ark.
- Translator: uses translation and dictionary services.
- Debug Browser: opens URLs entered by the user.

Do not enter secrets, production keys, or sensitive personal data into untrusted third-party services.

## Project Structure

```text
JavaFx_tools/
├── pom.xml
├── build-exe.ps1
├── 一键打包.bat
├── wordbook/
└── src/main/
    ├── java/com/devtool/
    │   ├── Launcher.java
    │   ├── Main.java
    │   ├── config/
    │   ├── controller/
    │   ├── util/
    │   └── view/
    └── resources/
        ├── css/
        ├── fxml/
        ├── images/
        └── logback.xml
```

Key concepts:

- `PageEnum`: registers all tool pages.
- `MainController`: handles navigation, page switching, and page caching.
- `BaseController`: provides `onPageInit()` and `onPageDestroy()` lifecycle hooks.
- `controller`: JavaFX interaction logic.
- `util`: reusable business logic.
- `resources/fxml`: page layouts.
- `resources/css/global.css`: global theme styles.

## Development Guide

To add a new tool page:

1. Add a new FXML file under `src/main/resources/fxml/`.
2. Add a Controller under `src/main/java/com/devtool/controller/` and extend `BaseController`.
3. Put reusable logic under `src/main/java/com/devtool/util/` when needed.
4. Register the page in `PageEnum`.

Recommendations:

- Reuse existing styles in `global.css`.
- Run long tasks on background threads to avoid blocking the JavaFX UI thread.
- Never commit API keys, tokens, or personal data.
- Keep shared logic in `util` instead of overloading Controllers.

## Contributing

Issues, suggestions, and pull requests are welcome.

Suggested workflow:

1. Fork this repository.
2. Create a focused feature branch.
3. Keep unrelated formatting changes out of the PR.
4. Run the build before submitting:

```powershell
mvn -q -DskipTests package
```

5. Describe what changed, how it was tested, and any potential impact.

## Security

- Do not commit API keys, tokens, cookies, or production credentials.
- Rotate any key that has appeared in chat logs, screenshots, or Git history.
- The AI assistant sends prompts to the configured model provider; review your provider's data policy.
- Before publishing packaged builds, check `logs/`, `wordbook/`, and local config files to avoid leaking personal data.

## Roadmap

- Add more AI provider presets.
- Add unit tests for core utility classes.
- Add screenshots to the README.
- Move release notes to a dedicated `CHANGELOG.md`.
- Improve cross-platform packaging.

## License

This project is open-sourced under the [MIT License](LICENSE).

## Contact

- Author: Jactil
- Email: `jactil777@gmail.com`
