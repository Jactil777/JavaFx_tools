package com.devtool.controller;

/**
 * 所有页面控制器基类
 * 每个功能页面的 Controller 都应继承此类
 */
public abstract class BaseController {

    /**
     * 页面激活/切换时触发，用于初始化/刷新页面数据
     */
    public abstract void onPageInit();

    /**
     * 页面失活/隐藏时触发，子类按需重写（如停止定时器、释放资源等）
     */
    public void onPageDestroy() {
        // 子类按需重写
    }
}

