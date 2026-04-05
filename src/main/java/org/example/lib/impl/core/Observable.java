package org.example.lib.impl.core;

import org.example.lib.api.core.Disposable;
import org.example.lib.api.core.Emitter;
import org.example.lib.api.core.ObservableSource;
import org.example.lib.api.core.Observer;
import org.example.lib.api.scheduler.Scheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Consumer;

public class Observable<T> {

    private final ObservableSource<T> source;

    private Observable(ObservableSource<T> source) {
        this.source = source;
    }

    public static <T> Observable<T> create(ObservableSource<T> source) {
        if (source == null) throw new NullPointerException("source must not be null");
        return new Observable<>(source);
    }

    public static <T> Observable<T> error(Throwable t) {
        return create(emitter -> emitter.onError(t));
    }

    public static <T> Observable<T> empty() {
        return create(emitter -> emitter.onComplete());
    }

    public Disposable subscribe(Observer<T> observer) {
        if (observer == null) throw new NullPointerException("observer must not be null");

        SimpleDisposable disposable = new SimpleDisposable();
        Emitter<T> emitter = new SafeEmitter<>(observer, disposable);

        try {
            source.subscribe(emitter);
        } catch (Throwable t) {
            if (!disposable.isDisposed()) {
                observer.onError(t);
            }
        }

        return disposable;
    }

    public Disposable subscribe(Consumer<T> onNext) {
        return subscribe(new Observer<T>() {
            @Override public void onNext(T item) { onNext.accept(item); }
            @Override public void onError(Throwable t) { }
            @Override public void onComplete() { }
        });
    }

    public Disposable subscribe(Consumer<T> onNext, Consumer<Throwable> onError) {
        return subscribe(new Observer<T>() {
            @Override public void onNext(T item) { onNext.accept(item); }
            @Override public void onError(Throwable t) { onError.accept(t); }
            @Override public void onComplete() {}
        });
    }

    public Disposable subscribe(Consumer<T> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        return subscribe(new Observer<T>() {
            @Override public void onNext(T item) { onNext.accept(item); }
            @Override public void onError(Throwable t) { onError.accept(t); }
            @Override public void onComplete() { onComplete.run(); }
        });
    }

    public <R> Observable<R> map(Function<T, R> mapper) {
        if (mapper == null) throw new NullPointerException("mapper must not be null");
        return create(emitter -> this.subscribe(new Observer<>() {
            @Override
            public void onNext(T item) {
                if (emitter.isDisposed()) return;
                try {
                    R result = mapper.apply(item);
                    emitter.onNext(result);
                } catch (Throwable t) {
                    emitter.onError(t);
                }
            }

            @Override
            public void onError(Throwable t) {
                emitter.onError(t);
            }

            @Override
            public void onComplete() {
                emitter.onComplete();
            }
        }));
    }

    public Observable<T> filter(Predicate<T> predicate) {
        if (predicate == null) throw new NullPointerException("predicate must not be null");
        return create(emitter -> this.subscribe(new Observer<>() {
            @Override
            public void onNext(T item) {
                if (emitter.isDisposed()) return;
                try {
                    if (predicate.test(item)) {
                        emitter.onNext(item);
                    }
                } catch (Throwable t) {
                    emitter.onError(t);
                }
            }

            @Override
            public void onError(Throwable t) {
                emitter.onError(t);
            }

            @Override
            public void onComplete() {
                emitter.onComplete();
            }
        }));
    }

    public <R> Observable<R> flatMap(Function<T, Observable<R>> mapper) {
        if (mapper == null) throw new NullPointerException("mapper must not be null");
        return create(emitter -> {
            AtomicBoolean hasError = new AtomicBoolean(false);

            this.subscribe(new Observer<T>() {
                @Override
                public void onNext(T item) {
                    if (emitter.isDisposed() || hasError.get()) return;
                    try {
                        Observable<R> inner = mapper.apply(item);
                        if (inner == null) throw new NullPointerException("flatMap mapper returned null Observable");

                        inner.subscribe(new Observer<R>() {
                            @Override
                            public void onNext(R r) {
                                if (!emitter.isDisposed()) emitter.onNext(r);
                            }

                            @Override
                            public void onError(Throwable t) {
                                if (hasError.compareAndSet(false, true)) {
                                    emitter.onError(t);
                                }
                            }

                            @Override
                            public void onComplete() {
                                // inner завершился — ничего не делаем, ждём outer
                            }
                        });
                    } catch (Throwable t) {
                        if (hasError.compareAndSet(false, true)) {
                            emitter.onError(t);
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (hasError.compareAndSet(false, true)) {
                        emitter.onError(t);
                    }
                }

                @Override
                public void onComplete() {
                    if (!hasError.get()) emitter.onComplete();
                }
            });
        });
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        if (scheduler == null) throw new NullPointerException("scheduler must not be null");
        return create(emitter -> scheduler.execute(() -> {
            if (!emitter.isDisposed()) {
                this.subscribe(new Observer<T>() {
                    @Override public void onNext(T item) { emitter.onNext(item); }
                    @Override public void onError(Throwable t) { emitter.onError(t); }
                    @Override public void onComplete() { emitter.onComplete(); }
                });
            }
        }));
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        if (scheduler == null) throw new NullPointerException("scheduler must not be null");
        return create(emitter -> this.subscribe(new Observer<T>() {
            @Override
            public void onNext(T item) {
                scheduler.execute(() -> {
                    if (!emitter.isDisposed()) emitter.onNext(item);
                });
            }

            @Override
            public void onError(Throwable t) {
                scheduler.execute(() -> emitter.onError(t));
            }

            @Override
            public void onComplete() {
                scheduler.execute(() -> {
                    if (!emitter.isDisposed()) emitter.onComplete();
                });
            }
        }));
    }

    // для тестов
    public void blockingSubscribe(Observer<T> observer) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        subscribe(new Observer<T>() {
            @Override public void onNext(T item) { observer.onNext(item); }

            @Override
            public void onError(Throwable t) {
                observer.onError(t);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                observer.onComplete();
                latch.countDown();
            }
        });
        latch.await();
    }

    private static final class SafeEmitter<T> implements Emitter<T> {

        private final Observer<T> downstream;
        private final SimpleDisposable disposable;
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        SafeEmitter(Observer<T> downstream, SimpleDisposable disposable) {
            this.downstream = downstream;
            this.disposable = disposable;
        }

        @Override
        public void onNext(T item) {
            if (terminated.get() || disposable.isDisposed()) return;
            try {
                downstream.onNext(item);
            } catch (Throwable t) {
                onError(t);
            }
        }

        @Override
        public void onError(Throwable t) {
            if (terminated.compareAndSet(false, true)) {
                try {
                    downstream.onError(t);
                } finally {
                    disposable.dispose();
                }
            }
        }

        @Override
        public void onComplete() {
            if (terminated.compareAndSet(false, true)) {
                try {
                    downstream.onComplete();
                } finally {
                    disposable.dispose();
                }
            }
        }

        @Override
        public boolean isDisposed() {
            return disposable.isDisposed();
        }
    }
}
