package com.tyron.builder.internal.snpashot.impl;

import com.tyron.builder.internal.isolation.Isolatable;
import com.tyron.builder.internal.snapshot.ValueSnapshot;
import com.tyron.builder.internal.state.ManagedFactory;

import javax.annotation.Nullable;

public class IsolatedManagedValue extends AbstractManagedValueSnapshot<Isolatable<?>> implements Isolatable<Object> {
    private final ManagedFactory factory;
    private final Class<?> targetType;

    public IsolatedManagedValue(Class<?> targetType, ManagedFactory factory, Isolatable<?> state) {
        super(state);
        this.targetType = targetType;
        this.factory = factory;
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return new ManagedValueSnapshot(targetType.getName(), state.asSnapshot());
    }

    @Override
    public Object isolate() {
        return factory.fromState(targetType, state.isolate());
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(targetType)) {
            return type.cast(isolate());
        }
        return type.cast(factory.fromState(type, state.isolate()));
    }

    public int getFactoryId() {
        return factory.getId();
    }

    public Class<?> getTargetType() {
        return targetType;
    }
}
