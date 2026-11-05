package com.example.monolith.labs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原理实验的输出：统一加上 "[实验结果]" 前缀，跑完后从测试日志里 grep 出来，就是文档里 > [!example] 的原始素材。
 */
public final class LabReport {

    private static final Logger log = LoggerFactory.getLogger("LAB");

    private LabReport() {}

    public static void print(String format, Object... args) {
        log.info("[实验结果] " + format, args);
    }
}
