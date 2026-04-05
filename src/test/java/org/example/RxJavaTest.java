package org.example;

import org.example.lib.api.core.Disposable;
import org.example.lib.api.core.Observer;
import org.example.lib.impl.core.Observable;
import org.example.lib.impl.core.SimpleDisposable;
import org.example.lib.impl.scheduler.ComputationScheduler;
import org.example.lib.impl.scheduler.IOThreadScheduler;
import org.example.lib.impl.scheduler.SingleThreadScheduler;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RxJavaTest {

    @Nested
    @DisplayName("1. Observable — базовые компоненты")
    class ObservableBasicTest {

        @Test
        @DisplayName("create() доставляет onNext и onComplete в правильном порядке")
        void testCreateAndSubscribe() {
            List<Integer> received = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean(false);

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onNext(2);
                emitter.onNext(3);
                emitter.onComplete();
            }).subscribe(
                    item -> received.add(item),
                    t    -> fail("Unexpected error: " + t),
                    ()   -> completed.set(true)
            );

            assertEquals(List.of(1, 2, 3), received);
            assertTrue(completed.get());
        }

        @Test
        @DisplayName("create(null) бросает NullPointerException")
        void testCreateNullSource() {
            assertThrows(NullPointerException.class, () -> Observable.create(null));
        }

        @Test
        @DisplayName("subscribe(null Observer) бросает NullPointerException")
        void testSubscribeNullObserver() {
            Observable<Integer> obs = Observable.create(emitter -> emitter.onComplete());
            assertThrows(NullPointerException.class, () -> obs.subscribe((Observer<Integer>) null));
        }

        @Test
        @DisplayName("Observable.empty() вызывает только onComplete")
        void testEmpty() {
            AtomicBoolean completed = new AtomicBoolean(false);
            List<Object> items = new ArrayList<>();

            Observable.empty().subscribe(
                    items::add,
                    t  -> fail("Unexpected error"),
                    () -> completed.set(true)
            );

            assertTrue(items.isEmpty());
            assertTrue(completed.get());
        }

        @Test
        @DisplayName("Observable.error() сразу вызывает onError")
        void testErrorFactory() {
            RuntimeException ex = new RuntimeException("forced");
            AtomicReference<Throwable> caught = new AtomicReference<>();
            AtomicBoolean completed = new AtomicBoolean(false);

            Observable.<Integer>error(ex).subscribe(
                    item -> fail("Should not receive items"),
                    caught::set,
                    () -> completed.set(true)
            );

            assertSame(ex, caught.get());
            assertFalse(completed.get());
        }

        @Test
        @DisplayName("После onComplete повторный onNext игнорируется")
        void testNoEventsAfterComplete() {
            List<Integer> received = new ArrayList<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onComplete();
                emitter.onNext(2);
            }).subscribe(received::add);

            assertEquals(List.of(1), received);
        }

        @Test
        @DisplayName("После onError повторный onNext игнорируется")
        void testNoEventsAfterError() {
            List<Integer> received = new ArrayList<>();
            AtomicInteger errorCount = new AtomicInteger(0);

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onError(new RuntimeException("err"));
                emitter.onNext(2);
            }).subscribe(
                    received::add,
                    t -> errorCount.incrementAndGet()
            );

            assertEquals(List.of(1), received);
            assertEquals(1, errorCount.get());
        }

        @Test
        @DisplayName("subscribe() возвращает ненулевой Disposable")
        void testSubscribeReturnsDisposable() {
            Disposable d = Observable.<Integer>create(emitter -> emitter.onComplete())
                    .subscribe(item -> {});
            assertNotNull(d);
        }

        @Test
        @DisplayName("Упрощённая subscribe(onNext) работает корректно")
        void testSimpleLambdaSubscribe() {
            List<String> result = new ArrayList<>();

            Observable.<String>create(emitter -> {
                emitter.onNext("a");
                emitter.onNext("b");
                emitter.onComplete();
            }).subscribe(result::add);

            assertEquals(List.of("a", "b"), result);
        }
    }

    @Nested
    @DisplayName("2. Операторы — map, filter, flatMap")
    class OperatorsTest {

        @Test
        @DisplayName("map() преобразует каждый элемент")
        void testMap() {
            List<String> result = new ArrayList<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onNext(2);
                emitter.onNext(3);
                emitter.onComplete();
            })
            .map(x -> "item-" + x)
            .subscribe(result::add);

            assertEquals(List.of("item-1", "item-2", "item-3"), result);
        }

        @Test
        @DisplayName("map() передаёт исключение из mapper в onError")
        void testMapThrowsError() {
            AtomicReference<Throwable> error = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onComplete();
            })
            .map(x -> { throw new IllegalStateException("map error"); })
            .subscribe(
                    item -> fail("Should not receive items"),
                    error::set
            );

            assertInstanceOf(IllegalStateException.class, error.get());
            assertEquals("map error", error.get().getMessage());
        }

        @Test
        @DisplayName("map(null) бросает NullPointerException")
        void testMapNullMapper() {
            assertThrows(NullPointerException.class,
                    () -> Observable.create(e -> {}).map(null));
        }

        @Test
        @DisplayName("filter() пропускает только подходящие элементы")
        void testFilter() {
            List<Integer> result = new ArrayList<>();

            Observable.<Integer>create(emitter -> {
                for (int i = 1; i <= 6; i++) emitter.onNext(i);
                emitter.onComplete();
            })
            .filter(x -> x % 2 == 0)
            .subscribe(result::add);

            assertEquals(List.of(2, 4, 6), result);
        }

        @Test
        @DisplayName("filter() с predicate всегда false — пустой результат, onComplete вызывается")
        void testFilterAllBlocked() {
            List<Integer> result = new ArrayList<>();
            AtomicBoolean completed = new AtomicBoolean(false);

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onNext(2);
                emitter.onComplete();
            })
            .filter(x -> false)
            .subscribe(result::add, t -> {}, () -> completed.set(true));

            assertTrue(result.isEmpty());
            assertTrue(completed.get());
        }

        @Test
        @DisplayName("filter() передаёт исключение из predicate в onError")
        void testFilterThrowsError() {
            AtomicReference<Throwable> error = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onComplete();
            })
            .filter(x -> { throw new RuntimeException("filter error"); })
            .subscribe(item -> fail("Should not receive"), error::set);

            assertNotNull(error.get());
            assertEquals("filter error", error.get().getMessage());
        }

        @Test
        @DisplayName("filter(null) бросает NullPointerException")
        void testFilterNullPredicate() {
            assertThrows(NullPointerException.class,
                    () -> Observable.create(e -> {}).filter(null));
        }

        @Test
        @DisplayName("flatMap() разворачивает вложенные Observable в единый поток")
        void testFlatMap() {
            List<Integer> result = new ArrayList<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onNext(2);
                emitter.onNext(3);
                emitter.onComplete();
            })
            .flatMap(x -> Observable.<Integer>create(emitter -> {
                emitter.onNext(x * 10);
                emitter.onNext(x * 10 + 1);
                emitter.onComplete();
            }))
            .subscribe(result::add);

            assertEquals(6, result.size());
            assertTrue(result.containsAll(List.of(10, 11, 20, 21, 30, 31)));
        }

        @Test
        @DisplayName("flatMap() пробрасывает ошибку из внутреннего Observable")
        void testFlatMapInnerError() {
            AtomicReference<Throwable> error = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onComplete();
            })
            .flatMap(x -> Observable.<Integer>create(emitter -> {
                emitter.onError(new RuntimeException("inner error"));
            }))
            .subscribe(
                    item -> fail("Should not receive items"),
                    error::set
            );

            assertNotNull(error.get());
            assertEquals("inner error", error.get().getMessage());
        }

        @Test
        @DisplayName("flatMap(null) бросает NullPointerException")
        void testFlatMapNullMapper() {
            assertThrows(NullPointerException.class,
                    () -> Observable.create(e -> {}).flatMap(null));
        }

        @Test
        @DisplayName("Цепочка filter + map + flatMap работает корректно")
        void testOperatorChain() {
            List<String> result = new ArrayList<>();

            Observable.<Integer>create(emitter -> {
                for (int i = 1; i <= 5; i++) emitter.onNext(i);
                emitter.onComplete();
            })
            .filter(x -> x % 2 != 0)
            .map(x -> x * 10)
            .flatMap(x -> Observable.<String>create(emitter -> {
                emitter.onNext("value=" + x);
                emitter.onComplete();
            }))
            .subscribe(result::add);

            assertEquals(List.of("value=10", "value=30", "value=50"), result);
        }
    }

    @Nested
    @DisplayName("3. Schedulers — subscribeOn и observeOn")
    class SchedulersTest {

        @Test
        @DisplayName("IOThreadScheduler выполняет задачи в потоке io-thread-*")
        void testIOSchedulerThread() throws InterruptedException {
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            IOThreadScheduler io = new IOThreadScheduler();
            io.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(threadName.get().startsWith("io-thread-"));
            io.shutdown();
        }

        @Test
        @DisplayName("ComputationScheduler: parallelism == availableProcessors()")
        void testComputationParallelism() {
            ComputationScheduler comp = new ComputationScheduler();
            assertEquals(Runtime.getRuntime().availableProcessors(), comp.getParallelism());
            comp.shutdown();
        }

        @Test
        @DisplayName("ComputationScheduler выполняет задачи в потоке computation-thread-*")
        void testComputationSchedulerThread() throws InterruptedException {
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            ComputationScheduler comp = new ComputationScheduler();
            comp.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
            });

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(threadName.get().startsWith("computation-thread-"));
            comp.shutdown();
        }

        @Test
        @DisplayName("SingleThreadScheduler: все задачи выполняются в потоке single-thread")
        void testSingleThreadSequential() throws InterruptedException {
            List<String> names = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch latch = new CountDownLatch(3);

            SingleThreadScheduler single = new SingleThreadScheduler();
            for (int i = 0; i < 3; i++) {
                single.execute(() -> {
                    names.add(Thread.currentThread().getName());
                    latch.countDown();
                });
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, names.stream().distinct().count());
            assertEquals("single-thread", names.get(0));
            single.shutdown();
        }

        @Test
        @DisplayName("subscribeOn() переносит генерацию данных в io-поток")
        void testSubscribeOn() throws InterruptedException {
            String mainThread = Thread.currentThread().getName();
            AtomicReference<String> sourceThread = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                sourceThread.set(Thread.currentThread().getName());
                emitter.onNext(1);
                emitter.onComplete();
            })
            .subscribeOn(new IOThreadScheduler())
            .blockingSubscribe(new Observer<Integer>() {
                @Override public void onNext(Integer item) {}
                @Override public void onError(Throwable t) {}
                @Override public void onComplete() {}
            });

            assertNotEquals(mainThread, sourceThread.get());
            assertTrue(sourceThread.get().startsWith("io-thread-"));
        }

        @Test
        @DisplayName("observeOn() переносит доставку событий в single-поток")
        void testObserveOn() throws InterruptedException {
            String mainThread = Thread.currentThread().getName();
            AtomicReference<String> observeThread = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(42);
                emitter.onComplete();
            })
            .observeOn(new SingleThreadScheduler())
            .blockingSubscribe(new Observer<Integer>() {
                @Override
                public void onNext(Integer item) {
                    observeThread.set(Thread.currentThread().getName());
                }
                @Override public void onError(Throwable t) {}
                @Override public void onComplete() {}
            });

            assertNotEquals(mainThread, observeThread.get());
            assertEquals("single-thread", observeThread.get());
        }

        @Test
        @DisplayName("subscribeOn + observeOn: генерация и доставка в разных потоках")
        void testSubscribeOnAndObserveOn() throws InterruptedException {
            AtomicReference<String> sourceThread = new AtomicReference<>();
            AtomicReference<String> observeThread = new AtomicReference<>();

            Observable.<String>create(emitter -> {
                sourceThread.set(Thread.currentThread().getName());
                emitter.onNext("data");
                emitter.onComplete();
            })
            .subscribeOn(new IOThreadScheduler())
            .map(String::toUpperCase)
            .observeOn(new SingleThreadScheduler())
            .blockingSubscribe(new Observer<String>() {
                @Override
                public void onNext(String item) {
                    observeThread.set(Thread.currentThread().getName());
                }
                @Override public void onError(Throwable t) {}
                @Override public void onComplete() {}
            });

            assertTrue(sourceThread.get().startsWith("io-thread-"));
            assertEquals("single-thread", observeThread.get());
            assertNotEquals(sourceThread.get(), observeThread.get());
        }

        @Test
        @DisplayName("subscribeOn(null) бросает NullPointerException")
        void testSubscribeOnNull() {
            assertThrows(NullPointerException.class,
                    () -> Observable.create(e -> {}).subscribeOn(null));
        }

        @Test
        @DisplayName("observeOn(null) бросает NullPointerException")
        void testObserveOnNull() {
            assertThrows(NullPointerException.class,
                    () -> Observable.create(e -> {}).observeOn(null));
        }
    }

    @Nested
    @DisplayName("4. Disposable — управление подписками")
    class DisposableTest {

        @Test
        @DisplayName("Новый SimpleDisposable не отменён")
        void testInitialState() {
            SimpleDisposable d = new SimpleDisposable();
            assertFalse(d.isDisposed());
        }

        @Test
        @DisplayName("После dispose() isDisposed() == true")
        void testDisposeChangesState() {
            SimpleDisposable d = new SimpleDisposable();
            d.dispose();
            assertTrue(d.isDisposed());
        }

        @Test
        @DisplayName("Повторный dispose() не бросает исключений (идемпотентен)")
        void testDoubleDispose() {
            SimpleDisposable d = new SimpleDisposable();
            d.dispose();
            assertDoesNotThrow(d::dispose);
            assertTrue(d.isDisposed());
        }

        @Test
        @DisplayName("isDisposed() потокобезопасен при вызове из нескольких потоков")
        void testThreadSafeDispose() throws InterruptedException {
            SimpleDisposable d = new SimpleDisposable();
            int threads = 10;
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                new Thread(() -> {
                    d.dispose();
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(d.isDisposed());
        }

        @Test
        @DisplayName("subscribe() возвращает disposed=true после завершения потока")
        void testDisposedAfterComplete() {
            Disposable d = Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onComplete();
            }).subscribe(item -> {});

            assertTrue(d.isDisposed());
        }

        @Test
        @DisplayName("subscribe() возвращает disposed=true после ошибки")
        void testDisposedAfterError() {
            Disposable d = Observable.<Integer>create(emitter -> {
                emitter.onError(new RuntimeException("err"));
            }).subscribe(item -> {}, t -> {});

            assertTrue(d.isDisposed());
        }
    }

    @Nested
    @DisplayName("5. Обработка ошибок")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Исключение внутри ObservableSource передаётся в onError")
        void testExceptionInSource() {
            RuntimeException ex = new RuntimeException("source error");
            AtomicReference<Throwable> caught = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                throw ex;
            }).subscribe(
                    item -> fail("Should not receive items"),
                    caught::set
            );

            assertSame(ex, caught.get());
        }

        @Test
        @DisplayName("onError вызывается только один раз при множественных ошибках")
        void testOnlyOneErrorDelivered() {
            AtomicInteger errorCount = new AtomicInteger(0);

            Observable.<Integer>create(emitter -> {
                emitter.onError(new RuntimeException("first"));
                emitter.onError(new RuntimeException("second"));
            }).subscribe(
                    item -> {},
                    t -> errorCount.incrementAndGet()
            );

            assertEquals(1, errorCount.get());
        }

        @Test
        @DisplayName("После onError вызов onComplete игнорируется")
        void testNoCompleteAfterError() {
            AtomicBoolean completed = new AtomicBoolean(false);

            Observable.<Integer>create(emitter -> {
                emitter.onError(new RuntimeException("err"));
                emitter.onComplete();
            }).subscribe(
                    item -> {},
                    t -> {},
                    () -> completed.set(true)
            );

            assertFalse(completed.get());
        }

        @Test
        @DisplayName("NPE в mapper попадает в onError")
        void testMapNullPointerInMapper() {
            AtomicReference<Throwable> error = new AtomicReference<>();

            Observable.<String>create(emitter -> {
                emitter.onNext(null);
                emitter.onComplete();
            })
            .map(s -> s.toUpperCase())
            .subscribe(
                    item -> fail("Should not receive"),
                    error::set
            );

            assertInstanceOf(NullPointerException.class, error.get());
        }

        @Test
        @DisplayName("Ошибка в subscribeOn-потоке доставляется через onError")
        void testErrorInAsyncThread() throws InterruptedException {
            AtomicReference<Throwable> error = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                throw new RuntimeException("async error");
            })
            .subscribeOn(new IOThreadScheduler())
            .blockingSubscribe(new Observer<Integer>() {
                @Override public void onNext(Integer item) {}
                @Override public void onError(Throwable t) { error.set(t); }
                @Override public void onComplete() {}
            });

            assertNotNull(error.get());
            assertEquals("async error", error.get().getMessage());
        }

        @Test
        @DisplayName("onError из flatMap внутреннего потока останавливает весь поток")
        void testFlatMapErrorStopsStream() {
            List<Integer> received = new ArrayList<>();
            AtomicReference<Throwable> error = new AtomicReference<>();

            Observable.<Integer>create(emitter -> {
                emitter.onNext(1);
                emitter.onNext(2);
                emitter.onNext(3);
                emitter.onComplete();
            })
            .flatMap(x -> Observable.<Integer>create(emitter -> {
                if (x == 2) {
                    emitter.onError(new RuntimeException("stop at 2"));
                } else {
                    emitter.onNext(x);
                    emitter.onComplete();
                }
            }))
            .subscribe(received::add, error::set);

            assertNotNull(error.get());
            assertEquals("stop at 2", error.get().getMessage());
        }
    }
}
