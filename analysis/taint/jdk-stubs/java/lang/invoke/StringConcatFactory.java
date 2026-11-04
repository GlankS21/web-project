package java.lang.invoke;

/** Analysis-only stub: lets a JDK 8 library model resolve Java 9+ string-concat bootstraps. */
public final class StringConcatFactory {
    private StringConcatFactory() {}

    public static CallSite makeConcat(MethodHandles.Lookup lookup, String name, MethodType concatType) {
        return null;
    }

    public static CallSite makeConcatWithConstants(MethodHandles.Lookup lookup, String name,
            MethodType concatType, String recipe, Object... constants) {
        return null;
    }
}
