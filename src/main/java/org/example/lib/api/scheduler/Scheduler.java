package org.example.lib.api.scheduler;

public interface Scheduler {

    void execute(Runnable task);

    default void shutdown() {}
}