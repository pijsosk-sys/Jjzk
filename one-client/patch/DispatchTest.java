import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Runs the patched dispatcher bytecode and checks how many steps it takes in each state. */
public final class DispatchTest {
    public static void main(String[] args) throws Exception {
        Class<?> host = Class.forName("TickHostUnderTest");
        Method tick = host.getMethod("tick", Object.class);
        Method idle = host.getMethod("rex$idle");
        Field running = host.getField("running");
        Field pending = host.getField("pending");
        Field prepared = host.getField("prepared");

        Object instance = host.getDeclaredConstructor().newInstance();
        running.set(instance, true);
        check("idle with an empty queue", (Boolean)idle.invoke(instance));
        pending.set(instance, new Object());
        check("busy while a placement is in flight", !(Boolean)idle.invoke(instance));
        pending.set(instance, null);
        prepared.set(instance, new Object());
        check("busy while a placement is armed", !(Boolean)idle.invoke(instance));
        prepared.set(instance, null);

        TempoStub.reset(3, Integer.MAX_VALUE);
        tick.invoke(instance, new Object());
        check("three steps while idle, got " + TempoStub.calls, TempoStub.calls == 3);
        check("retry check runs once per tick", TempoStub.before == 1);

        TempoStub.reset(3, 1);
        prepared.set(instance, null);
        tick.invoke(instance, new Object());
        check("stops after arming, got " + TempoStub.calls, TempoStub.calls == 1);

        TempoStub.reset(1, Integer.MAX_VALUE);
        prepared.set(instance, null);
        tick.invoke(instance, new Object());
        check("one step when the tempo module is off, got " + TempoStub.calls, TempoStub.calls == 1);

        running.set(instance, false);
        TempoStub.reset(4, Integer.MAX_VALUE);
        tick.invoke(instance, new Object());
        check("one step while stopped, got " + TempoStub.calls, TempoStub.calls == 1);
        System.out.println("dispatcher: all checks passed");
    }

    static void check(String what, boolean ok) {
        if (!ok) {
            throw new AssertionError(what);
        }
        System.out.println("  ok: " + what);
    }
}
