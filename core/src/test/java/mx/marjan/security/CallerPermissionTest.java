package mx.marjan.security;

import java.math.BigDecimal;
import java.util.List;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Caller seam: services must consult the injected caller instead of the
 * desktop-only static {@link Session}, so a web backend can supply a
 * request-scoped identity. A denied call short-circuits before touching the
 * database, which keeps this test fast and database-free.
 */
class CallerPermissionTest {

    @Test
    void deniedCallerIsConsultedAndBlocksTheUseCase() {
        RecordingCaller caller = new RecordingCaller(false);
        RouteService service = new RouteService(caller);

        var result = service.save(new Route(0, "A", "B", BigDecimal.TEN, ""));

        assertTrue(caller.hasCalled, "the service must read permissions from the injected caller");
        assertEquals(List.of("No tiene permiso para modificar rutas"), result.problems());
    }

    private static final class RecordingCaller implements Caller {

        private final boolean allowed;
        private boolean hasCalled;

        RecordingCaller(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public long userId() {
            return 42;
        }

        @Override
        public boolean has(String permission) {
            hasCalled = true;
            return allowed;
        }
    }
}
