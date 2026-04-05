package org.example.lib.impl.core;

import org.example.lib.api.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleDisposable implements Disposable {

    private final AtomicBoolean disposed = new AtomicBoolean(false);

    @Override
    public void dispose() {
        disposed.set(true);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}