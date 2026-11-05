package com.example.monolith.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.monolith.support.ApiClient;
import com.example.monolith.support.DemoAccounts;
import com.example.monolith.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * 并发刷新：模拟前端没有做"只刷新一次"时的情况——多个请求同时拿同一个 refresh token 来刷新。
 *
 * <p>后端的保证：只有一个成功；其余的被当成重放，整个会话作废。
 * 这也从后端角度证明了前端为什么必须用 single flight 保证并发 401 只刷新一次。
 */
@IntegrationTest
class RefreshTokenConcurrencyIT {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenConcurrencyIT.class);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
    }

    @Test
    void 同一个refresh_token并发刷新_只有一个成功_其余按重放处理() throws Exception {
        String refreshToken = api.login(DemoAccounts.EDITOR).get("refreshToken").asString();
        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<ApiClient.Response>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    // 所有线程在起跑线等着，一起出发，尽量制造真正的并发
                    startGate.await();
                    return api.refresh(refreshToken);
                }));
            }
            startGate.countDown();
            List<ApiClient.Response> responses = new ArrayList<>();
            for (Future<ApiClient.Response> future : futures) {
                responses.add(future.get());
            }

            List<ApiClient.Response> succeeded =
                    responses.stream().filter(r -> r.code() == 0).toList();
            long rejected = responses.stream().filter(r -> r.status() == 401).count();
            log.info("[实验结果] 并发刷新 {} 次：成功 {} 次，401 {} 次", threads, succeeded.size(), rejected);

            assertThat(succeeded).hasSize(1);
            assertThat(rejected).isEqualTo(threads - 1);

            // 赢家拿到的新 token 也不能用了：重放检测已经作废了整个会话
            String winnerRefresh =
                    succeeded.getFirst().data().get("refreshToken").asString();
            String winnerAccess = succeeded.getFirst().data().get("accessToken").asString();
            assertThat(api.refresh(winnerRefresh).status()).isEqualTo(401);
            assertThat(api.getJson("/api/auth/profile", winnerAccess).status()).isEqualTo(401);
        }
    }
}
