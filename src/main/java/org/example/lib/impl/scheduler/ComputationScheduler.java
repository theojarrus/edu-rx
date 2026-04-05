package org.example.lib.impl.scheduler;

import org.example.lib.api.scheduler.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ComputationScheduler implements Scheduler {

    private final int parallelism = Runtime.getRuntime().availableProcessors();
    private final ExecutorService executor = Executors.newFixedThreadPool(parallelism, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("computation-thread-" + thread.getId());
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

    public int getParallelism() {
        return parallelism;
    }
}