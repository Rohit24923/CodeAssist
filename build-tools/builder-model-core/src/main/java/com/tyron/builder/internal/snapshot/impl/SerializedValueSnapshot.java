package com.tyron.builder.internal.snapshot.impl;


import com.google.common.base.Objects;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.tyron.builder.internal.hash.Hashes;
import com.tyron.builder.internal.snapshot.ValueSnapshot;
import com.tyron.builder.internal.snapshot.ValueSnapshotter;
import com.tyron.builder.internal.snapshot.ValueSnapshottingException;

import org.apache.commons.io.input.ClassLoaderObjectInputStream;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * An immutable snapshot of the state of some value.
 */
public class SerializedValueSnapshot implements ValueSnapshot {
    private final HashCode implementationHash;
    private final byte[] serializedValue;

    public SerializedValueSnapshot(@Nullable HashCode implementationHash, byte[] serializedValue) {
        this.implementationHash = implementationHash;
        this.serializedValue = serializedValue;
    }

    @Nullable
    public HashCode getImplementationHash() {
        return implementationHash;
    }

    public byte[] getValue() {
        return serializedValue;
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (hasSameSerializedValue(value, snapshot)) {
            return this;
        }
        return snapshot;
    }

    private boolean hasSameSerializedValue(Object value, ValueSnapshot snapshot) {
        if (snapshot instanceof SerializedValueSnapshot) {
            SerializedValueSnapshot newSnapshot = (SerializedValueSnapshot) snapshot;
            if (!Objects.equal(implementationHash, newSnapshot.implementationHash)) {
                // Different implementation - assume value has changed
                return false;
            }
            if (Arrays.equals(serializedValue, newSnapshot.serializedValue)) {
                // Same serialized content - value has not changed
                return true;
            }

            // Deserialize the old value and use the equals() implementation. This will be removed at some point
            Object oldValue = populateClass(value.getClass());
            if (oldValue.equals(value)) {
//                DeprecationLogger.deprecateIndirectUsage("Using objects as inputs that have a different serialized form but are equal")
//                        .withContext("Type '" + value.getClass().getName() + "' has a custom implementation for equals().")
//                        .withAdvice("Declare the property as @Nested instead to expose its properties as inputs.")
//                        .willBeRemovedInGradle8()
//                        .withUserManual("upgrading_version_7", "equals_up_to_date_deprecation")
//                        .nagUser();
//                // Same value
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        if (implementationHash == null) {
            hasher.putInt(0);
        } else {
            Hashes.putHash(hasher, implementationHash);
        }
        hasher.putBytes(serializedValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        SerializedValueSnapshot other = (SerializedValueSnapshot) obj;
        return Objects.equal(implementationHash, other.implementationHash) && Arrays.equals(serializedValue, other.serializedValue);
    }

    protected Object populateClass(Class<?> originalClass) {
        Object populated;
        try {
            populated = new ClassLoaderObjectInputStream(originalClass.getClassLoader(), new ByteArrayInputStream(serializedValue)).readObject();
        } catch (Exception e) {
            throw new ValueSnapshottingException("Couldn't populate class " + originalClass.getName(), e);
        }
        return populated;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serializedValue);
    }
}
