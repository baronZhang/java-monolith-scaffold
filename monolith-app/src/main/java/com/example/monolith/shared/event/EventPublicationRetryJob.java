package com.example.monolith.shared.event;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 事件发布登记表的维护任务：重投失败的事件、清理已完成的记录。
 *
 * <p>Spring Modulith 的事件发布登记表（event_publication 表）在发布事件的那个事务里，为每个监听器记一条记录；
 * 监听器执行成功才标记为完成，抛异常就标记为失败。这个任务定期把失败的重新投递一次——
 * 和微服务里"本地消息表 + 定时补偿"是同一个思路，只是全在一个进程、一个数据库里。
 *
 * <p>重投意味着监听器可能被执行多次（至少一次投递），所以监听器必须幂等，见库存模块的 OrderEventListener。
 */
@Component
@ConditionalOnProperty(name = "app.events.retry-enabled", havingValue = "true", matchIfMissing = true)
public class EventPublicationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(EventPublicationRetryJob.class);

    private final FailedEventPublications failedPublications;
    private final CompletedEventPublications completedPublications;

    public EventPublicationRetryJob(
            FailedEventPublications failedPublications, CompletedEventPublications completedPublications) {
        this.failedPublications = failedPublications;
        this.completedPublications = completedPublications;
    }

    @Scheduled(
            fixedDelayString = "${app.events.retry-interval:PT1M}",
            initialDelayString = "${app.events.retry-interval:PT1M}")
    public void resubmitFailed() {
        // 只重投失败超过 30 秒的：给刚失败的留一点时间，避免和正在恢复的下游打架
        failedPublications.resubmit(ResubmissionOptions.defaults()
                .withMinAge(Duration.ofSeconds(30))
                .withBatchSize(100));
        log.debug("已触发失败事件重投");
    }

    /** 已完成的记录只是留作排查用，保留 7 天后删除，避免这张表无限增长。 */
    @Scheduled(cron = "${app.events.purge-cron:0 30 3 * * *}")
    public void purgeCompleted() {
        completedPublications.deletePublicationsOlderThan(Duration.ofDays(7));
        log.info("已清理 7 天前完成的事件发布记录");
    }
}
