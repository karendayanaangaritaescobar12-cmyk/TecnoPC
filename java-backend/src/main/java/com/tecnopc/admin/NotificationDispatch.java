package com.tecnopc.admin;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

/** Wake the persistent outbox after commit. No provider call runs in the business transaction. */
@Component
public class NotificationDispatch {
    private final NotificationWorker worker;
    private final TaskExecutor executor;
    public NotificationDispatch(NotificationWorker worker,@Qualifier("notificationDispatchExecutor") TaskExecutor executor){this.worker=worker;this.executor=executor;}
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterCommit(CustomerNotifications.PendingNotifications event){executor.execute(worker::drain);}

    @Configuration
    public static class Execution {
        @Bean("notificationDispatchExecutor")
        public ThreadPoolTaskExecutor notificationDispatchExecutor(){
            var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(1);executor.setQueueCapacity(1);executor.setThreadNamePrefix("tecnopc-notifications-");
            // Excess wakeups can be coalesced: all work remains durably queued and scheduled drain is retained.
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
            executor.setWaitForTasksToCompleteOnShutdown(true);executor.setAwaitTerminationSeconds(30);return executor;
        }
    }
}
