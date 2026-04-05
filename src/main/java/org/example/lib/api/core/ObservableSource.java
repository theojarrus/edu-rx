package org.example.lib.api.core;

public interface ObservableSource<T> {

    void subscribe(Emitter<T> emitter);
}
