package com.example.monolith.labs.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.monolith.labs.LabReport;
import com.example.trace.MdcTaskDecorator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 原理实验：CompletableFuture 的并行聚合、默认线程池、异常和超时处理，以及异步线程里 MDC（traceId）丢失的问题。
 */
class CompletableFutureLabTest {

    @Test
    void 三个互不依赖的远程调用_串行和并行的耗时() {
        long serialStart = System.nanoTime();
        String serial = remote("订单", 100) + remote("商品", 100) + remote("库存", 100);
        long serialMillis = (System.nanoTime() - serialStart) / 1_000_000;

        long parallelMillis;
        try (ExecutorService io = Executors.newFixedThreadPool(3)) {
            long start = System.nanoTime();
            CompletableFuture<String> order = CompletableFuture.supplyAsync(() -> remote("订单", 100), io);
            CompletableFuture<String> product = CompletableFuture.supplyAsync(() -> remote("商品", 100), io);
            CompletableFuture<String> stock = CompletableFuture.supplyAsync(() -> remote("库存", 100), io);
            String parallel = order.thenCombine(product, String::concat)
                    .thenCombine(stock, String::concat)
                    .join();
            parallelMillis = (System.nanoTime() - start) / 1_000_000;
            assertThat(parallel).isEqualTo(serial);
        }

        LabReport.print("三个各 100 ms 的调用：串行 {} ms，并行（自定义线程池）{} ms", serialMillis, parallelMillis);
        assertThat(parallelMillis).isLessThan(serialMillis * 2 / 3);
    }

    @Test
    void 不传线程池时_用的是全局共享的ForkJoinPool() {
        String threadName = CompletableFuture.supplyAsync(
                        () -> Thread.currentThread().getName())
                .join();

        LabReport.print(
                "supplyAsync 不传线程池：执行线程 {}，公共池并行度 {}（默认是 CPU 核数 - 1，整个 JVM 共用一个）",
                threadName,
                ForkJoinPool.commonPool().getParallelism());
        assertThat(threadName).startsWith("ForkJoinPool.commonPool-worker-");
    }

    @Test
    void 异常处理_exceptionally兜底_join会包一层CompletionException() {
        CompletableFuture<String> failing = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("下游服务出错");
        });

        String fallback = failing.exceptionally(ex -> "兜底数据").join();
        assertThat(fallback).isEqualTo("兜底数据");

        assertThatThrownBy(failing::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        LabReport.print("异常：exceptionally 返回兜底值；不处理时 join() 抛 CompletionException，真正的异常在 getCause() 里");
    }

    @Test
    void 超时控制_completeOnTimeout给默认值_orTimeout直接失败() {
        String withDefault = CompletableFuture.supplyAsync(() -> remote("慢服务", 500))
                .completeOnTimeout("默认值", 50, TimeUnit.MILLISECONDS)
                .join();
        assertThat(withDefault).isEqualTo("默认值");

        assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> remote("慢服务", 500))
                        .orTimeout(50, TimeUnit.MILLISECONDS)
                        .join())
                .hasCauseInstanceOf(TimeoutException.class);
        LabReport.print("超时：completeOnTimeout 超时后返回默认值；orTimeout 超时后以 TimeoutException 结束（底层任务并不会被中断）");
    }

    @Test
    void 异步线程拿不到MDC里的traceId_用TaskDecorator传过去() throws Exception {
        MDC.put("traceId", "trace-42");
        try (ExecutorService plain = Executors.newSingleThreadExecutor()) {
            String lost = CompletableFuture.supplyAsync(() -> String.valueOf(MDC.get("traceId")), plain)
                    .join();

            ThreadPoolTaskExecutor decorated = new ThreadPoolTaskExecutor();
            decorated.setTaskDecorator(new MdcTaskDecorator());
            decorated.initialize();
            String kept = CompletableFuture.supplyAsync(() -> String.valueOf(MDC.get("traceId")), decorated)
                    .join();
            decorated.shutdown();

            LabReport.print("异步线程里的 traceId：普通线程池拿到 {}；装了 MdcTaskDecorator 的线程池拿到 {}", lost, kept);
            assertThat(lost).isEqualTo("null");
            assertThat(kept).isEqualTo("trace-42");
        } finally {
            MDC.clear();
        }
    }

    private static String remote(String name, long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return name;
    }
}
