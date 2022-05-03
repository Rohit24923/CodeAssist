package com.tyron.builder.api.internal;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.tyron.builder.api.Action;
import com.tyron.builder.api.DomainObjectCollection;
import com.tyron.builder.api.internal.collections.ElementSource;
import com.tyron.builder.api.internal.provider.CollectionProviderInternal;
import com.tyron.builder.api.internal.provider.ProviderInternal;
import com.tyron.builder.internal.Actions;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A domain object collection that presents a combined view of one or more collections.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectSet<T> extends DelegatingDomainObjectSet<T> implements WithEstimatedSize {

    private final Predicate<T> uniqueSpec = new ItemIsUniqueInCompositeSpec();
    private final Predicate<T> notInSpec = new ItemNotInCompositeSpec();

    private final DefaultDomainObjectSet<T> backingSet;
    private final CollectionCallbackActionDecorator callbackActionDecorator;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        return create(type, CollectionCallbackActionDecorator.NOOP, collections);
    }

    @SafeVarargs
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, CollectionCallbackActionDecorator callbackActionDecorator, DomainObjectCollection<? extends T>... collections) {
        DefaultDomainObjectSet<T> backingSet = new DefaultDomainObjectSet<T>(type, new DomainObjectCompositeCollection<T>(), callbackActionDecorator);
        CompositeDomainObjectSet<T> out = new CompositeDomainObjectSet<T>(backingSet, callbackActionDecorator);
        for (DomainObjectCollection<? extends T> c : collections) {
            out.addCollection(c);
        }
        return out;
    }

    private CompositeDomainObjectSet(DefaultDomainObjectSet<T> backingSet, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(backingSet);
        this.backingSet = backingSet;
        this.callbackActionDecorator = callbackActionDecorator;
    }

    public class ItemIsUniqueInCompositeSpec implements Predicate<T> {
        @Override
        public boolean test(T element) {
            int matches = 0;
            for (DomainObjectCollection<? extends T> collection : getStore().store) {
                if (collection.contains(element)) {
                    if (++matches > 1) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public class ItemNotInCompositeSpec implements Predicate<T> {
        @Override
        public boolean test(T element) {
            return !getStore().contains(element);
        }
    }

    @SuppressWarnings("unchecked")
    protected DomainObjectCompositeCollection<T> getStore() {
        return (DomainObjectCompositeCollection) this.backingSet.getStore();
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.filter(action, uniqueSpec));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.filter(action, notInSpec));
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().containsCollection(collection)) {
            getStore().addComposited(collection);
            collection.all(new InternalAction<T>() {
                @Override
                public void execute(T t) {
                    backingSet.getEventRegister().fireObjectAdded(t);
                }
            });
            collection.whenObjectRemoved(new Action<T>() {
                @Override
                public void execute(T t) {
                    backingSet.getEventRegister().fireObjectRemoved(t);
                }
            });
        }
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        getStore().removeComposited(collection);
        for (T item : collection) {
            backingSet.getEventRegister().fireObjectRemoved(item);
        }
    }

    @SuppressWarnings({"NullableProblems", "unchecked"})
    @Override
    public Iterator<T> iterator() {
        return getStore().iterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * This method is expensive. Avoid calling it if possible. If all you need is a rough
     * estimate, call {@link #estimatedSize()} instead.
     */
    public int size() {
        return getStore().size();
    }

    @Override
    public int estimatedSize() {
        return getStore().estimatedSize();
    }

    @Override
    public void all(Action<? super T> action) {
        //calling overloaded method with extra behavior:
        whenObjectAdded(action);
        for (T t : this) {
            callbackActionDecorator.decorate(action).execute(t);
        }
    }

    // TODO Make this work with pending elements
    private final static class DomainObjectCompositeCollection<T> implements ElementSource<T> {

        private final List<DomainObjectCollection<? extends T>> store = Lists.newLinkedList();

        public boolean containsCollection(DomainObjectCollection<? extends T> collection) {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (ts == collection) {
                    return true;
                }
            }
            return false;
        }

        Set<T> collect() {
            if (store.isEmpty()) {
                return Collections.emptySet();
            }
            Set<T> tmp = Sets.newLinkedHashSetWithExpectedSize(estimatedSize());
            for (DomainObjectCollection<? extends T> collection : store) {
                tmp.addAll(collection);
            }
            return tmp;
        }

        @Override
        public int size() {
            return collect().size();
        }

        @Override
        public boolean isEmpty() {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (!ts.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean contains(Object o) {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (ts.contains(o)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
            if (store.isEmpty()) {
                return Collections.emptyIterator();
            }
            if (store.size() == 1) {
                return (Iterator<T>) store.get(0).iterator();
            }
            return collect().iterator();
        }

        @Override
        public boolean add(T t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addRealized(T element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public void addComposited(DomainObjectCollection<? extends T> collection) {
            this.store.add(collection);
        }

        public void removeComposited(DomainObjectCollection<? extends T> collection) {
            Iterator<DomainObjectCollection<? extends T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                DomainObjectCollection<? extends T> next = iterator.next();
                if (next == collection) {
                    iterator.remove();
                    break;
                }
            }
        }

        @Override
        public boolean constantTimeIsEmpty() {
            return store.isEmpty();
        }

        @Override
        public int estimatedSize() {
            int size = 0;
            for (DomainObjectCollection<? extends T> ts : store) {
                size += Estimates.estimateSizeOf(ts);
            }
            return size;
        }

        @Override
        public Iterator<T> iteratorNoFlush() {
            return iterator();
        }

        @Override
        public void realizePending() {

        }

        @Override
        public void realizePending(Class<?> type) {

        }

        @Override
        public boolean addPending(ProviderInternal<? extends T> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removePending(ProviderInternal<? extends T> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onRealize(Action<T> action) {

        }

        @Override
        public void realizeExternal(ProviderInternal<? extends T> provider) {

        }

        @Override
        public MutationGuard getMutationGuard() {
            return MutationGuards.identity();
        }
    }
}
