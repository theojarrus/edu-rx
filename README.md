### Observable

Центральный класс. Представляет холодный поток данных — генерация не начинается до вызова `subscribe()`.

```java
Observable.<Integer>create(emitter -> {
    emitter.onNext(1);
    emitter.onNext(2);
    emitter.onComplete();
})
.filter(x -> x > 0)
.map(x -> "result: " + x)
.subscribe(
    item -> System.out.println(item),
    err  -> err.printStackTrace(),
    ()   -> System.out.println("Done")
);
```

### Observer

Получает три типа сигналов:

| Метод | Когда вызывается |
|---|---|
| `onNext(T item)` | При каждом новом элементе (0..N раз) |
| `onError(Throwable t)` | При ошибке — завершает поток |
| `onComplete()` | При успешном завершении |

### Операторы

| Оператор | Описание |
|---|---|
| `map(Function)` | Преобразует каждый элемент |
| `filter(Predicate)` | Пропускает только подходящие элементы |
| `flatMap(Function)` | Преобразует элемент в новый Observable и разворачивает |

### Disposable

Позволяет отменить подписку. Возвращается из `subscribe()`:

```java
Disposable d = observable.subscribe(item -> ...);
d.dispose();         // отменить подписку
d.isDisposed();      // проверить состояние
```

---

## Schedulers

### IOThreadScheduler — `Executors.newCachedThreadPool()`

Динамически создаёт потоки по мере необходимости, переиспользует завершившиеся.

```java
observable.subscribeOn(new IOThreadScheduler())
```

### ComputationScheduler — `Executors.newFixedThreadPool(N)`

Фиксированный пул с числом потоков = `Runtime.getRuntime().availableProcessors()`.

```java
observable.subscribeOn(new ComputationScheduler())
```

### SingleThreadScheduler — `Executors.newSingleThreadExecutor()`

Один поток, все задачи выполняются строго последовательно.

```java
observable.observeOn(new SingleThreadScheduler())
```

---

## subscribeOn vs observeOn

```
observable
    .subscribeOn(new IOThreadScheduler())   ← в каком потоке ГЕНЕРИРУЮТСЯ данные
    .map(...)                               ← выполняется в io-потоке
    .observeOn(new SingleThreadScheduler()) ← в каком потоке ДОСТАВЛЯЮТСЯ события
    .subscribe(item -> updateUI(item));     ← вызывается в single-thread
```

| Метод | Что переносит | Типичный сценарий |
|---|---|---|
| `subscribeOn(s)` | Вызов `ObservableSource.subscribe()` | Перенести I/O в фоновый поток |
| `observeOn(s)` | Вызов `onNext` / `onError` / `onComplete` | Вернуть результат в UI-поток |

---

## Тесты

Файл: `src/test/java/org/example/RxJavaTest.java`

| Блок | Описание | Тестов |
|---|---|---|
| `ObservableBasicTest` | create, empty, error, порядок событий | 8 |
| `OperatorsTest` | map, filter, flatMap, цепочки | 10 |
| `SchedulersTest` | имена потоков, subscribeOn, observeOn | 8 |
| `DisposableTest` | состояние, идемпотентность, потокобезопасность | 6 |
| `ErrorHandlingTest` | исключения в source/mapper/async | 6 |
| **Итого** | | **38** |

```bash
./gradlew test
# Отчёт: build/reports/tests/test/index.html
```

---

## Пример: асинхронная загрузка

```java
Observable.<String>create(emitter -> {
    // выполняется в io-thread
    String data = fetchFromNetwork("https://api.example.com/data");
    emitter.onNext(data);
    emitter.onComplete();
})
.subscribeOn(new IOThreadScheduler())
.map(String::toUpperCase)
.observeOn(new SingleThreadScheduler())
.subscribe(
    result -> System.out.println("[single-thread] " + result),
    err    -> System.err.println("Error: " + err.getMessage())
);
```
