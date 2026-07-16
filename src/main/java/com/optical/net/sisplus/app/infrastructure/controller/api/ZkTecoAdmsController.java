package com.optical.net.sisplus.app.infrastructure.controller.api;

import com.optical.net.sisplus.app.infrastructure.entity.ZkDevice;
import com.optical.net.sisplus.app.application.PortAdapter;
import com.optical.net.sisplus.app.domain.UserDomain;
import com.optical.net.sisplus.app.infrastructure.service.ZkDeviceService;
import com.optical.net.sisplus.app.infrastructure.service.UserService;
import com.optical.net.sisplus.app.infrastructure.zkteco.ZkTecoConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/iclock")
@RequiredArgsConstructor
public class ZkTecoAdmsController {

    private final UserService userService;
    private final ZkDeviceService deviceService;
    private final PortAdapter portAdapter;

    private static final java.util.Map<Integer, String> VERIFY_METHODS = java.util.Map.ofEntries(
            java.util.Map.entry(0,  "Contraseña"),
            java.util.Map.entry(1,  "Huella dactilar"),
            java.util.Map.entry(2,  "Tarjeta"),
            java.util.Map.entry(3,  "Contraseña + Huella"),
            java.util.Map.entry(4,  "Contraseña + Tarjeta"),
            java.util.Map.entry(5,  "Huella + Tarjeta"),
            java.util.Map.entry(6,  "Contraseña + Huella + Tarjeta"),
            java.util.Map.entry(10, "Rostro"),
            java.util.Map.entry(11, "Rostro + Huella"),
            java.util.Map.entry(12, "Rostro + Contraseña"),
            java.util.Map.entry(13, "Rostro + Tarjeta"),
            java.util.Map.entry(15, "Vena del dedo"),
            java.util.Map.entry(20, "Palma"),
            java.util.Map.entry(255,"Automático")
    );


    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handshake(
            @RequestParam(value = "SN", required = false) String sn,
            @RequestParam(value = "options", required = false) String options,
            @RequestParam(value = "pushver", required = false) String pushver,
            @RequestParam(value = "language", required = false) String language,
            HttpServletRequest request) {

        if (sn == null || sn.isBlank()) {
            log.warn("[ZKTeco] Handshake sin SN desde IP: {}", request.getRemoteAddr());
            return ResponseEntity.ok("error");
        }

        log.info("[ZKTeco] Handshake — SN={}, pushver={}, IP={}",
                sn, pushver, request.getRemoteAddr());

        if ("all".equals(options)) {
            ZkDevice device = deviceService.registerOrUpdate(
                    sn, request.getRemoteAddr(), pushver, language);
            return ResponseEntity.ok(deviceService.buildHandshakeResponse(device));
        }

        return ResponseEntity.ok("error");
    }

    // =========================================================
    //  2. RECEPCIÓN DE DATOS
    //     POST /iclock/cdata?SN=xxx&table=ATTLOG
    // =========================================================

    @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveData(
            @RequestParam(value = "SN", required = false) String sn,
            @RequestParam(value = "table", required = false) String table,
            @RequestParam(value = "Stamp", required = false) String stamp,
            HttpServletRequest request) {

        if (sn == null || sn.isBlank()) {
            return ResponseEntity.ok("OK");
        }

        try {
            String body = new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));

            log.debug("[ZKTeco] POST /cdata — SN={}, table={}", sn, table);

            if (ZkTecoConstants.TABLE_ATTLOG.equalsIgnoreCase(table)) {
                int count = processAttendanceLogs(body, sn);
                return ResponseEntity.ok("OK: " + count);

            } else if (ZkTecoConstants.TABLE_OPERLOG.equalsIgnoreCase(table)) {
                processOperLog(body, sn);

            } else if (ZkTecoConstants.TABLE_OPTIONS.equalsIgnoreCase(table)) {
                log.debug("[ZKTeco] Config push de SN={}: {}", sn, body);

            } else {
                log.debug("[ZKTeco] Tabla no manejada: table={}, SN={}", table, sn);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            log.error("[ZKTeco] Error en POST /cdata de SN={}", sn, e);
            return ResponseEntity.ok("OK");
        }
    }

    // =========================================================
    //  3. HEARTBEAT / COLA DE COMANDOS
    //     GET /iclock/getrequest?SN=xxx
    // =========================================================

    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRequest(
            @RequestParam(value = "SN", required = false) String sn,
            @RequestParam(value = "INFO", required = false) String info) {

        if (sn == null || sn.isBlank()) {
            return ResponseEntity.ok("OK");
        }

        log.debug("[ZKTeco] Heartbeat — SN={}", sn);

        if (info != null && !info.isBlank()) {
            deviceService.updateDeviceInfo(sn, info);
        }

        String response = deviceService.buildGetRequestResponse(sn);
        if (!"OK".equals(response)) {
            log.info("[ZKTeco] Enviando comandos a SN={}", sn);
        }
        return ResponseEntity.ok(response);
    }

    // =========================================================
    //  4. RESPUESTA A COMANDOS
    //     POST /iclock/devicecmd
    // =========================================================

    @PostMapping(value = "/devicecmd", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deviceCommandResponse(
            @RequestParam(value = "SN", required = false) String sn,
            HttpServletRequest request) {
        try {
            String body = new BufferedReader(new InputStreamReader(request.getInputStream()))
                    .lines().collect(Collectors.joining("&"));

            String id         = extractParam(body, "ID");
            String returnCode = extractParam(body, "Return");
            String returnInfo = extractParam(body, "CMD");

            if (sn != null && id != null && returnCode != null) {
                deviceService.processCommandResponse(sn, id, returnCode, returnInfo);
            }
        } catch (Exception e) {
            log.error("[ZKTeco] Error en devicecmd de SN={}", sn, e);
        }
        return ResponseEntity.ok("OK");
    }

    // =========================================================
    //  Procesamiento de ATTLOG
    // =========================================================

    private int processAttendanceLogs(String body, String sn) {
        if (body == null || body.isBlank()) return 0;

        String[] lines = body.split("\n");
        int processed = 0;
        String lastTimestamp = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            try {
                String[] parts = line.split("\t");
                if (parts.length < 2) {
                    log.warn("[ZKTeco] Línea ATTLOG con formato inesperado: {}", line);
                    continue;
                }

                String pin       = parts[0].trim();
                String timestamp = parts[1].trim();
                int status       = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
                int verify       = parts.length > 3 ? parseIntSafe(parts[3]) : -1;

                // ─── LOG BONITO EN CONSOLA ───────────────────────
                logAttendanceEvent(pin, timestamp, status, verify, sn);
                // ─────────────────────────────────────────────────

                UserDomain user = findUserByZkPin(pin);
                if (user == null) {
                    log.warn("[ZKTeco] PIN={} no encontrado en la base de datos", pin);
                    continue;
                }

                switch (status) {
                    case ZkTecoConstants.ATT_STATUS_CHECK_IN  -> userService.registerEntry(user.getId());
                    case ZkTecoConstants.ATT_STATUS_CHECK_OUT -> userService.registerExit(user.getId());
                    case ZkTecoConstants.ATT_STATUS_AUTO      -> userService.registerEntry(user.getId());
                    default -> log.debug("[ZKTeco] Status {} no manejado para PIN={}", status, pin);
                }

                lastTimestamp = timestamp;
                processed++;

            } catch (Exception e) {
                log.error("[ZKTeco] Error procesando línea ATTLOG '{}': {}", line, e.getMessage());
            }
        }

        if (lastTimestamp != null) {
            deviceService.updateAttLogStamp(sn, lastTimestamp);
        }

        return processed;
    }

    /**
     * Imprime en consola un bloque visual por cada marcación.
     *
     * Ejemplo de salida:
     *
     * ┌─────────────────────────────────────────────────┐
     * │  🖐  MARCACIÓN RECIBIDA                          │
     * │  PIN        : 5                                  │
     * │  Hora       : 2026-02-28 08:30:00                │
     * │  Tipo       : ENTRADA                            │
     * │  Método     : Huella dactilar                    │
     * │  Dispositivo: ABC123456                          │
     * └─────────────────────────────────────────────────┘
     */
    private void logAttendanceEvent(String pin, String timestamp,
                                    int status, int verify, String deviceSn) {
        String tipo   = resolveStatusLabel(status);
        String metodo = VERIFY_METHODS.getOrDefault(verify, "Desconocido (código " + verify + ")");
        String emoji  = resolveEmoji(status, verify);

        log.info(String.format(
                "%n┌─────────────────────────────────────────────────┐" +
                        "%n│  %s  MARCACIÓN RECIBIDA" +
                        "%n│  PIN        : %-34s│" +
                        "%n│  Hora       : %-34s│" +
                        "%n│  Tipo       : %-34s│" +
                        "%n│  Método     : %-34s│" +
                        "%n│  Dispositivo: %-34s│" +
                        "%n└─────────────────────────────────────────────────┘",
                emoji, pin, timestamp, tipo, metodo, deviceSn
        ));
    }

    private String resolveStatusLabel(int status) {
        return switch (status) {
            case ZkTecoConstants.ATT_STATUS_CHECK_IN  -> "ENTRADA";
            case ZkTecoConstants.ATT_STATUS_CHECK_OUT -> "SALIDA";
            case ZkTecoConstants.ATT_STATUS_BREAK_OUT -> "SALIDA DESCANSO";
            case ZkTecoConstants.ATT_STATUS_BREAK_IN  -> "REGRESO DESCANSO";
            case ZkTecoConstants.ATT_STATUS_AUTO      -> "AUTOMÁTICO";
            default                                    -> "OTRO (" + status + ")";
        };
    }

    private String resolveEmoji(int status, int verify) {
        String base = switch (verify) {
            case 1  -> "🖐 ";
            case 2  -> "💳";
            case 10 -> "😊";
            case 15 -> "🖐 ";
            case 20 -> "✋";
            default -> "🔐";
        };
        return status == ZkTecoConstants.ATT_STATUS_CHECK_OUT ? base + "↩" : base;
    }

    // =========================================================
    //  Procesamiento de OPERLOG
    // =========================================================

    private void processOperLog(String body, String sn) {
        if (body == null || body.isBlank()) return;

        for (String line : body.split("\n")) {
            line = line.trim();
            if (line.isBlank()) continue;

            if (line.startsWith(ZkTecoConstants.OPERLOG_PREFIX_USER)) {
                log.info("[ZKTeco] USER data de SN={}: {}", sn, line);
            } else if (line.startsWith(ZkTecoConstants.OPERLOG_PREFIX_FP)) {
                log.info("[ZKTeco] 🖐  Huella recibida — SN={}, PIN={}",
                        sn, extractTabParam(line, "PIN"));
            } else if (line.startsWith(ZkTecoConstants.OPERLOG_PREFIX_FACE)) {
                log.info("[ZKTeco] 😊 Rostro recibido — SN={}, PIN={}",
                        sn, extractTabParam(line, "PIN"));
            } else {
                log.debug("[ZKTeco] OPERLOG — SN={}: {}", sn, line);
            }
        }
    }

    // =========================================================
    //  Helpers
    // =========================================================

    private UserDomain findUserByZkPin(String pin) {
        try {
            return portAdapter.findUserByZkPin(pin.trim());
        } catch (Exception e) {
            log.warn("[ZKTeco] PIN={} no encontrado: {}", pin, e.getMessage());
            return null;
        }
    }

    private String extractParam(String body, String key) {
        for (String part : body.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0].trim())) return kv[1].trim();
        }
        return null;
    }

    private String extractTabParam(String line, String key) {
        for (String part : line.split("\t")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0].trim())) return kv[1].trim();
        }
        return null;
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }
}