import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

/** Local build workaround for Windows environments whose AF_UNIX implementation cannot connect.
 * Does not modify the installed JDK. Each affected JVM uses normal TCP loopback instead.
 * Never packaged in either mod jar.
 */
public final class LoopbackAgent {
    public static void premain(String options, Instrumentation instrumentation) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Unsafe unsafe = (Unsafe)field.get(null);
        Class<?> sockets = Class.forName("sun.nio.ch.UnixDomainSockets");
        Field supported = sockets.getDeclaredField("supported");
        unsafe.putBooleanVolatile(unsafe.staticFieldBase(supported), unsafe.staticFieldOffset(supported), false);
    }
}
