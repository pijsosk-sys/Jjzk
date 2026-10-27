import java.lang.reflect.Field;

/** Stands in for rex.BuildTempo and records what the patched dispatcher did. */
public final class TempoStub {
    public static int steps = 1;
    public static int calls;
    public static int before;
    public static int clearAfter = Integer.MAX_VALUE;

    public static void before(Object controller) {
        before++;
    }

    public static int steps() {
        return steps;
    }

    public static void step(Object host) {
        calls++;
        try {
            if (calls >= clearAfter) {
                Field prepared = host.getClass().getField("prepared");
                prepared.set(host, new Object());
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static void reset(int limit, int clear) {
        steps = limit;
        calls = 0;
        before = 0;
        clearAfter = clear;
    }
}
