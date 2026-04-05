package org.example.lib.impl.scheduler;

import org.example.lib.api.scheduler.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IOThreadScheduler implements Scheduler {

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("io-thread-" + thread.getId());
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }
}