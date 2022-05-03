package com.tyron.builder.api.internal.artifacts.transform;

import com.tyron.builder.internal.Try;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An invocation which can come from the cache.
 *
 * Invoking can be expensive when the result is not from the cache.
 *
 * @param <T> The type which will be computed.
 */
public interface CacheableInvocation<T> {

    /**
     * The result of the invocation when it is already in the cache.
     */
    Optional<Try<T>> getCachedResult();

    /**
     * Obtain the result of the invocation, either by returning the cached result or by computing it.
     */
    Try<T> invoke();

    /**
     * Maps the result of the invocation via a mapper.
     *
     * @param mapper An inexpensive function on the result.
     */
    default <U> CacheableInvocation<U> map(Function<? super T, U> mapper) {
        return new CacheableInvocation<U>() {
            @Override
            public Optional<Try<U>> getCachedResult() {
                return CacheableInvocation.this.getCachedResult().map(result -> result.map(mapper));
            }

            @Override
            public Try<U> invoke() {
                return CacheableInvocation.this.invoke().map(mapper);
            }
        };
    }

    /**
     * Chains two {@link CacheableInvocation}s.
     *
     * @param mapper A function which creates the next {@link CacheableInvocation} from the result of the first one.
     *               Creating the invocation may be expensive, so this method avoids calling the mapper twice if possible.
     */
    default <U> CacheableInvocation<U> flatMap(Function<? super T, CacheableInvocation<U>> mapper) {
        return getCachedResult()
            .map(cachedResult -> cachedResult
                .tryMap(mapper)
                .getOrMapFailure(failure -> cached(Try.failure(failure)))
            ).orElseGet(() ->
                nonCached(() ->
                    invoke().flatMap(intermediateResult -> mapper.apply(intermediateResult).invoke())
            )
        );
    }

    /**
     * An invocation returning a fixed result from the cache.
     */
    static <T> CacheableInvocation<T> cached(Try<T> result) {
        return new CacheableInvocation<T>() {
            @Override
            public Optional<Try<T>> getCachedResult() {
                return Optional.of(result);
            }

            @Override
            public Try<T> invoke() {
                return result;
            }
        };
    }

    /**
     * An invocation with no cached result, requiring to do the expensive computation on {@link #invoke}.
     */
    static <T> CacheableInvocation<T> nonCached(Supplier<Try<T>> result) {
        return new CacheableInvocation<T>() {
            @Override
            public Optional<Try<T>> getCachedResult() {
                return Optional.empty();
            }

            @Override
            public Try<T> invoke() {
                return result.get();
            }
        };
    }
}
