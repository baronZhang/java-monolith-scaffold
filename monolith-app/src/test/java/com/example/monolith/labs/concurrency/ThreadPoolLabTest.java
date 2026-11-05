package com.example.monolith.labs.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.labs.LabReport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 原理实验：线程池参数到底怎么起作用。所有任务先卡在一个闸门上（CountDownLatch），
 * 这样每提交一个任务，都能看清它是被新线程接走了、进了队列，还是被拒绝了。
 */
class ThreadPoolLabTest {

    @Test
    void 提交顺序_先核心线程_再队列_再加到最大线程_最后拒绝() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        // 核心 2、最大 4、队列容量 2
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy());
        List<String> steps = new ArrayList<>();
        try {
            for (int i = 1; i <= 7; i++) {
                try {
                    pool.execute(() -> await(gate));
                    steps.add("任务" + i + "：线程数=" + pool.getPoolSize() + "，队列="
                            + pool.getQueue().size());
                } catch (RejectedExecutionException ex) {
                    steps.add("任务" + i + "：被拒绝（线程和队列都满了）");
                }
            }
        } finally {
            gate.countDown();
            shutdown(pool);
        }

        steps.forEach(step -> LabReport.print("线程池（核心2/最大4/队列2）{}", step));
        assertThat(steps)
                .containsExactly(
                        "任务1：线程数=1，队列=0",
                        "任务2：线程数=2，队列=0",
                        "任务3：线程数=2，队列=1",
                        "任务4：线程数=2，队列=2",
                        "任务5：线程数=3，队列=2",
                        "任务6：线程数=4，队列=2",
                        "任务7：被拒绝（线程和队列都满了）");
    }

    @Test
    void 四种拒绝策略的效果() throws Exception {
        assertThat(rejectScenario(new ThreadPoolExecutor.AbortPolicy()))
                .isEqualTo("抛出 RejectedExecutionException；执行了 [1, 2]");
        assertThat(rejectScenario(new ThreadPoolExecutor.CallerRunsPolicy())).startsWith("没有异常；执行了 [3, 1, 2]");
        assertThat(rejectScenario(new ThreadPoolExecutor.DiscardPolicy())).isEqualTo("没有异常；执行了 [1, 2]");
        assertThat(rejectScenario(new ThreadPoolExecutor.DiscardOldestPolicy())).isEqualTo("没有异常；执行了 [1, 3]");
    }

    /**
     * 线程池只有 1 个线程、队列容量 1：任务 1 占住线程，任务 2 进队列，任务 3 触发拒绝策略。
     * 只有任务 1 会卡在闸门上，2、3 执行时只记录自己的编号。
     */
    private String rejectScenario(RejectedExecutionHandler policy) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Integer> executed = new CopyOnWriteArrayList<>();
        List<String> callerThreads = new CopyOnWriteArrayList<>();
        String submitter = Thread.currentThread().getName();
        ThreadPoolExecutor pool =
                new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), policy);
        String outcome = "没有异常";
        try {
            pool.execute(() -> {
                await(gate);
                executed.add(1);
            });
            pool.execute(() -> executed.add(2));
            try {
                pool.execute(() -> {
                    executed.add(3);
                    if (Thread.currentThread().getName().equals(submitter)) {
                        callerThreads.add(submitter);
                    }
                });
            } catch (RejectedExecutionException ex) {
                outcome = "抛出 RejectedExecutionException";
            }
        } finally {
            gate.countDown();
            shutdown(pool);
        }
        String result = outcome + "；执行了 " + executed
                + (callerThreads.isEmpty() ? "" : "（任务 3 在提交者线程 " + callerThreads.getFirst() + " 里执行）");
        LabReport.print("拒绝策略 {}：{}", policy.getClass().getSimpleName(), result);
        return result;
    }

    @Test
    void 无界队列_最大线程数形同虚设() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        // Executors.newFixedThreadPool 就是这样：LinkedBlockingQueue 没有容量上限
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            for (int i = 0; i < 50; i++) {
                pool.execute(() -> await(gate));
            }
            LabReport.print(
                    "无界队列（核心2/最大10）：提交 50 个阻塞任务后，线程数 {}，排队 {}——最大线程数永远用不上，任务堆积只会吃内存",
                    pool.getPoolSize(),
                    pool.getQueue().size());
            assertThat(pool.getPoolSize()).isEqualTo(2);
            assertThat(pool.getQueue()).hasSize(48);
        } finally {
            gate.countDown();
            shutdown(pool);
        }
    }

    @Test
    void 阻塞型任务_虚拟线程和固定线程池的对比() throws Exception {
        int tasks = 200;
        long sleepMillis = 50;

        long fixed = timeIt(Executors.newFixedThreadPool(20), tasks, sleepMillis);
        long virtual = timeIt(Executors.newVirtualThreadPerTaskExecutor(), tasks, sleepMillis);

        LabReport.print("{} 个任务、每个阻塞 {} ms：20 个平台线程的固定线程池耗时 {} ms，虚拟线程耗时 {} ms", tasks, sleepMillis, fixed, virtual);
        // 固定线程池理论值：200 / 20 × 50 = 500 ms；虚拟线程阻塞时让出载体线程，几乎同时完成
        assertThat(virtual).isLessThan(fixed / 2);
    }

    private static long timeIt(ExecutorService executor, int tasks, long sleepMillis) throws Exception {
        long start = System.nanoTime();
        try (executor) {
            for (int i = 0; i < tasks; i++) {
                executor.submit(() -> {
                    Thread.sleep(sleepMillis);
                    return null;
                });
            }
        }
        // try-with-resources 的 close() 会等所有任务执行完（JDK 19 起 ExecutorService 实现了 AutoCloseable）
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void await(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown(ThreadPoolExecutor pool) throws InterruptedException {
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
}
