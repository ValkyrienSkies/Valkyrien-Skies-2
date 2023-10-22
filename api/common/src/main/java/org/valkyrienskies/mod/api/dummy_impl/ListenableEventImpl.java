package org.valkyrienskies.mod.api.dummy_impl;

import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.core.api.event.EventConsumer;
import org.valkyrienskies.core.api.event.ListenableEvent;
import org.valkyrienskies.core.api.event.RegisteredListener;

class ListenableEventImpl<T> implements ListenableEvent<T> {
    @NotNull
    @Override
    public RegisteredListener on(@NotNull EventConsumer<? super T> eventConsumer) {
        return () -> {};
    }
}
