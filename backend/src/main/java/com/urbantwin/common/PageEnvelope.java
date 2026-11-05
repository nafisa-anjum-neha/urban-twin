package com.urbantwin.common;

import java.util.List;

/** Pageable list envelope: { content, total, page, size } (contract §7). */
public record PageEnvelope<T>(List<T> content, long total, int page, int size) {
    public static <T> PageEnvelope<T> of(org.springframework.data.domain.Page<T> p) {
        return new PageEnvelope<>(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
    }
}
