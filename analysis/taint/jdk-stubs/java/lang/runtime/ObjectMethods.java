package java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.TypeDescriptor;

/** Analysis-only stub: lets a JDK 8 library model resolve record equals/hashCode/toString bootstraps. */
public final class ObjectMethods {
    private ObjectMethods() {}

    public static Object bootstrap(MethodHandles.Lookup lookup, String methodName, TypeDescriptor type,
            Class<?> recordClass, String names, MethodHandle... getters) {
        return null;
    }
}
