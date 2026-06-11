package com.devtool.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局未捕获异常处理器：防止程序闪退，弹窗提示并记录日志
 */
public class ExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        log.error("未捕获异常 [thread={}]", t.getName(), e);
        String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        DialogUtil.error("程序异常", "程序出现未处理异常：\n" + errMsg + "\n\n详细信息请查看日志文件");
    }
}

