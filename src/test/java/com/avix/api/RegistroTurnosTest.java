package com.avix.api;

import com.avix.api.service.RegistroService;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

class RegistroTurnosTest {
    @Test void turnosEnLimites() {
        assertEquals("A", RegistroService.calcularTurno(OffsetDateTime.parse("2026-10-14T06:00:00-05:00")).turno());
        assertEquals("B", RegistroService.calcularTurno(OffsetDateTime.parse("2026-10-14T14:00:00-05:00")).turno());
        assertEquals("C", RegistroService.calcularTurno(OffsetDateTime.parse("2026-10-14T22:00:00-05:00")).turno());
        assertEquals("2026-10-14", RegistroService.calcularTurno(
                OffsetDateTime.parse("2026-10-15T05:59:59-05:00")).fechaOperativa().toString());
        assertEquals("2026-10-15", RegistroService.calcularTurno(
                OffsetDateTime.parse("2026-10-15T06:00:00-05:00")).fechaOperativa().toString());
    }

    @Test void utcSeInterpretaComoHoraLima() {
        assertEquals("C", RegistroService.calcularTurno(
                OffsetDateTime.parse("2026-10-15T04:00:00Z")).turno());
    }
}
