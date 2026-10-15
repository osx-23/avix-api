package com.avix.api;

import com.avix.api.data.SupabaseData;
import com.avix.api.security.AvixAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginThrottleTest {
    @Test void bloqueaSeisIntentosDesdeMismoOrigen() {
        AvixAuth auth = new AvixAuth(mock(SupabaseData.class), "", true);
        for (int n = 0; n < 5; n++)
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> auth.login("CODE001", "invalida", "WEB", "ip-uno")).getStatusCode().value());
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> auth.login("CODE001", "invalida", "WEB", "ip-uno")).getStatusCode().value());
    }

    @Test void bloqueaDistribucionDeIntentosEntreIpsParaUnaCuenta() {
        AvixAuth auth = new AvixAuth(mock(SupabaseData.class), "", true);
        for (int n = 0; n < 25; n++) {
            final String origen = "ip-" + n;
            assertEquals(401, assertThrows(ResponseStatusException.class,
                    () -> auth.login("CODE002", "invalida", "WEB", origen)).getStatusCode().value());
        }
        assertEquals(429, assertThrows(ResponseStatusException.class,
                () -> auth.login("CODE002", "invalida", "WEB", "ip-otra")).getStatusCode().value());
    }

    @Test void puedeDesactivarseLoginMovilPorCodigo() {
        AvixAuth auth = new AvixAuth(mock(SupabaseData.class), "", false);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> auth.login("CODE003", null, "MOVIL", "ip-uno")).getStatusCode().value());
    }
}
