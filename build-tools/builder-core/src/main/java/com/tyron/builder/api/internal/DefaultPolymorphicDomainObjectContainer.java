package com.tyron.builder.api.internal;

import groovy.lang.Closure;
import com.tyron.builder.api.ExtensiblePolymorphicDomainObjectContainer;
import com.tyron.builder.api.InvalidUserDataException;
import com.tyron.builder.api.Named;
import com.tyron.builder.api.NamedDomainObjectFactory;
import com.tyron.builder.api.Namer;
import com.tyron.builder.internal.Cast;
import com.tyron.builder.internal.reflect.Instantiator;
import com.tyron.builder.model.internal.core.NamedEntityInstantiator;

import java.util.Set;

public class DefaultPolymorphicDomainObjectContainer<T> extends AbstractPolymorphicDomainObjectContainer<T>
    implements ExtensiblePolymorphicDomainObjectContainer<T> {
    protected final DefaultPolymorphicNamedEntityInstantiator<T> namedEntityInstantiator;
    private final Instantiator elementInstantiator;

    // NOTE: This constructor exists for backwards compatibility
    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer, CollectionCallbackActionDecorator callbackDecorator) {
        this(type, instantiator, instantiator, namer, callbackDecorator);
    }

    // NOTE: This constructor exists for backwards compatibility
    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, CollectionCallbackActionDecorator callbackDecorator) {
        this(type, instantiator, instantiator, Named.Namer.forType(type), callbackDecorator);
    }

    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Instantiator elementInstantiator, CollectionCallbackActionDecorator callbackDecorator) {
        this(type, instantiator, elementInstantiator, Named.Namer.forType(type), callbackDecorator);
    }

    private DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Instantiator elementInstantiator, Namer<? super T> namer, CollectionCallbackActionDecorator callbackDecorator) {
        super(type, instantiator, namer, callbackDecorator);
        this.namedEntityInstantiator = new DefaultPolymorphicNamedEntityInstantiator<T>(type, "this container");
        this.elementInstantiator = elementInstantiator;
    }

    @Override
    public NamedEntityInstantiator<T> getEntityInstantiator() {
        return namedEntityInstantiator;
    }

    @Override
    protected T doCreate(String name) {
        try {
            return namedEntityInstantiator.create(name, getType());
        } catch (InvalidUserDataException e) {
            if (e.getCause() instanceof NoFactoryRegisteredForTypeException) {
                throw new InvalidUserDataException(String.format("Cannot create a %s named '%s' because this container "
                    + "does not support creating elements by name alone. Please specify which subtype of %s to create. "
                    + "Known subtypes are: %s", getTypeDisplayName(), name, getTypeDisplayName(), namedEntityInstantiator.getSupportedTypeNames()));
            } else {
                throw e;
            }
        }
    }

    @Override
    protected <U extends T> U doCreate(String name, Class<U> type) {
        return namedEntityInstantiator.create(name, type);
    }

    public <U extends T> void registerDefaultFactory(NamedDomainObjectFactory<U> factory) {
        Class<T> castType = Cast.uncheckedCast(getType());
        registerFactory(castType, factory);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        namedEntityInstantiator.registerFactory(type, factory);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, final Closure<? extends U> factory) {
        registerFactory(type, new NamedDomainObjectFactory<U>() {
            @Override
            public U create(String name) {
                return factory.call(name);
            }
        });
    }

    @Override
    public <U extends T> void registerBinding(Class<U> type, final Class<? extends U> implementationType) {
        registerFactory(type, new NamedDomainObjectFactory<U>() {
            boolean named = Named.class.isAssignableFrom(implementationType);

            @Override
            public U create(String name) {
                return named ? elementInstantiator.newInstance(implementationType, name)
                    : elementInstantiator.newInstance(implementationType);
            }
        });
    }

    @Override
    public Set<? extends Class<? extends T>> getCreateableTypes() {
        return namedEntityInstantiator.getCreatableTypes();
    }
}
