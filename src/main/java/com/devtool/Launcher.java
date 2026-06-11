package com.devtool;

/**
 * 启动入口代理类
 * <p>
 * 原因：IDEA / java 命令直接运行时，JavaFX Application 子类的 main()
 * 会触发模块系统检查导致 "缺少 JavaFX 运行时组件" 错误。
 * 通过非 Application 子类的 main() 调用 Main.main()，可绕过此限制。
 * </p>
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}

