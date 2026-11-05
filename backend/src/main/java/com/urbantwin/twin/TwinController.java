package com.urbantwin.twin;

import com.urbantwin.common.PageEnvelope;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Digital twin dashboard endpoints (contract §7.11): a cached REST snapshot
 * (`/twin/state`) plus a 2 s SSE live feed (`/twin/live`) for the web UI.
 */
@RestController
@Tag(name = "twin")
@EnableScheduling
public class TwinController {

    private final TwinService service;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    TwinController(TwinService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/twin/state")
    public Map<String, Object> state() {
        return service.state();
    }

    @GetMapping(value = "/api/v1/twin/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; client disconnects clean up
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        // immediate snapshot so the UI paints straight away
        try {
            emitter.send(SseEmitter.event().name("state").data(service.state()));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Push the snapshot every 2 s; a light ping when nobody is listening. */
    @Scheduled(fixedRate = 2000)
    public void broadcast() {
        if (emitters.isEmpty()) return;
        Map<String, Object> snapshot = service.state();
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("state").data(snapshot));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        }
    }

    /** lightweight heartbeat alias used by the HA probe of the UI fallback. */
    @GetMapping("/api/v1/twin/ping")
    public ResponseEntity<PageEnvelope<java.util.UUID>> ping() {
        return ResponseEntity.ok(new PageEnvelope<>(java.util.List.of(), 0, 0, 0));
    }
}
