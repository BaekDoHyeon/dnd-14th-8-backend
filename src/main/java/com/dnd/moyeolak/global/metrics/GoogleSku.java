package com.dnd.moyeolak.global.metrics;

/**
 * Google Routes 과금 단위(SKU). compute_routes는 쿼리당, route_matrix는 엘리먼트당 과금된다.
 */
public enum GoogleSku {
    COMPUTE_ROUTES("compute_routes"),
    ROUTE_MATRIX("route_matrix");

    private final String tag;

    GoogleSku(String tag) {
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }
}
