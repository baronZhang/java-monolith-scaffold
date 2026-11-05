package com.example.trace;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * 把提交任务那一刻的 MDC 复制到执行任务的线程里。
 *
 * <p>MDC 底层是 ThreadLocal：@Async 方法、事件监听器换了线程执行，traceId 就丢了。
 * Spring Boot 会把容器里的 TaskDecorator Bean 自动应用到它创建的 applicationTaskExecutor 上，
 * 所以只要注册这个 Bean，异步任务的日志里也能带上 traceId。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 这一行在提交任务的线程里执行：拍下当时的 MDC
        Map<String, String> captured = MDC.getCopyOfContextMap();
        return () -> {
            // 下面在执行任务的线程里执行：先装上拍下的 MDC，跑完再恢复原样
            Map<String, String> previous = MDC.getCopyOfContextMap();
            replace(captured);
            try {
                runnable.run();
            } finally {
                replace(previous);
            }
        };
    }

    private static void replace(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
