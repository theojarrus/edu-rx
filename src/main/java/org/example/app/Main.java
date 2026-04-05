package org.example.app;

import org.example.lib.impl.core.Observable;
import org.example.lib.api.core.Observer;
import org.example.lib.api.core.Disposable;
import org.example.lib.impl.scheduler.IOThreadScheduler;
import org.example.lib.impl.scheduler.SingleThreadScheduler;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 1. Basic Observable ===");
        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        }).subscribe(
                item -> System.out.println("onNext: " + item),
                err  -> System.err.println("onError: " + err.getMessage()),
                ()   -> System.out.println("onComplete")
        );

        System.out.println("\n=== 2. map() ===");
        Observable.<Integer>create(emitter -> {
                    emitter.onNext(1);
                    emitter.onNext(2);
                    emitter.onNext(3);
                    emitter.onComplete();
                })
                .map(x -> x * 10)
                .subscribe(item -> System.out.println("mapped: " + item));

        System.out.println("\n=== 3. filter() ===");
        Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 6; i++) emitter.onNext(i);
                    emitter.onComplete();
                })
                .filter(x -> x % 2 == 0)
                .subscribe(item -> System.out.println("filtered: " + item));

        System.out.println("\n=== 4. map() + filter() chain ===");
        Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 5; i++) emitter.onNext(i);
                    emitter.onComplete();
                })
                .filter(x -> x % 2 != 0)
                .map(x -> "odd*10=" + (x * 10))
                .subscribe(item -> System.out.println(item));

        System.out.println("\n=== 5. flatMap() ===");
        Observable.<Integer>create(emitter -> {
                    emitter.onNext(1);
                    emitter.onNext(2);
                    emitter.onNext(3);
                    emitter.onComplete();
                })
                .flatMap(x -> Observable.<String>create(emitter -> {
                    emitter.onNext("A" + x);
                    emitter.onNext("B" + x);
                    emitter.onComplete();
                }))
                .subscribe(item -> System.out.println("flatMapped: " + item));

        System.out.println("\n=== 6. Error handling ===");
        Observable.<String>create(emitter -> {
                    emitter.onNext("hello");
                    emitter.onNext(null);
                    emitter.onComplete();
                })
                .map(String::toUpperCase)
                .subscribe(
                        item -> System.out.println("item: " + item),
                        err  -> System.out.println("caught: " + err.getClass().getSimpleName())
                );

        System.out.println("\n=== 7. Disposable ===");
        Disposable disposable = Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onNext(3);
            emitter.onComplete();
        }).subscribe(item -> System.out.println("received: " + item));

        System.out.println("isDisposed after complete: " + disposable.isDisposed());

        System.out.println("\n=== 8. subscribeOn (IOThreadScheduler) ===");
        Observable.<String>create(emitter -> {
                    System.out.println("source thread: " + Thread.currentThread().getName());
                    emitter.onNext("data");
                    emitter.onComplete();
                })
                .subscribeOn(new IOThreadScheduler())
                .blockingSubscribe(new Observer<String>() {
                    @Override public void onNext(String item) { System.out.println("onNext: " + item); }
                    @Override public void onError(Throwable t) { System.err.println(t.getMessage()); }
                    @Override public void onComplete() { System.out.println("onComplete"); }
                });

        System.out.println("\n=== 9. observeOn (SingleThreadScheduler) ===");
        Observable.<Integer>create(emitter -> {
                    emitter.onNext(42);
                    emitter.onComplete();
                })
                .observeOn(new SingleThreadScheduler())
                .blockingSubscribe(new Observer<Integer>() {
                    @Override public void onNext(Integer item) {
                        System.out.println("observe thread: " + Thread.currentThread().getName());
                        System.out.println("onNext: " + item);
                    }
                    @Override public void onError(Throwable t) {}
                    @Override public void onComplete() { System.out.println("onComplete"); }
                });

        System.out.println("\n=== 10. subscribeOn + observeOn ===");
        Observable.<String>create(emitter -> {
                    System.out.println("source thread: " + Thread.currentThread().getName());
                    emitter.onNext("reactive");
                    emitter.onComplete();
                })
                .subscribeOn(new IOThreadScheduler())
                .map(String::toUpperCase)
                .observeOn(new SingleThreadScheduler())
                .blockingSubscribe(new Observer<String>() {
                    @Override public void onNext(String item) {
                        System.out.println("observe thread: " + Thread.currentThread().getName());
                        System.out.println("onNext: " + item);
                    }
                    @Override public void onError(Throwable t) {}
                    @Override public void onComplete() { System.out.println("onComplete"); }
                });

        System.out.println("\n=== 11. Observable.empty() ===");
        Observable.empty().subscribe(
                item -> System.out.println("item: " + item),
                err  -> {},
                ()   -> System.out.println("completed immediately")
        );

        System.out.println("\n=== 12. Observable.error() ===");
        Observable.error(new RuntimeException("forced error")).subscribe(
                item -> {},
                err  -> System.out.println("caught error: " + err.getMessage())
        );

        System.out.println("\n=== Done ===");
    }
}