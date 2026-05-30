package com.jaroso.proyectosensores.dto;

public record CamaraHealthDto(String status, String camera_id, String model, Boolean has_state,
                              Boolean has_snapshot, Boolean has_shared_frame, String latest_timestamp) {
}
