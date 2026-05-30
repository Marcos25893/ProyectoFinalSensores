package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.CamaraEventsResponseDto;
import com.jaroso.proyectosensores.dto.CamaraHealthDto;
import com.jaroso.proyectosensores.dto.CamaraLatestDto;
import com.jaroso.proyectosensores.dto.CamaraStatsResponseDto;
import com.jaroso.proyectosensores.services.CamaraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/camara")
public class CamaraController {

    @Autowired
    private CamaraService camaraService;

    @GetMapping("/latest")
    public ResponseEntity<CamaraLatestDto> latest() {
        return ResponseEntity.ok(camaraService.getLatest());
    }

    @GetMapping("/health")
    public ResponseEntity<CamaraHealthDto> health() {
        return ResponseEntity.ok(camaraService.getHealth());
    }

    @GetMapping("/events")
    public ResponseEntity<CamaraEventsResponseDto> events(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String date_from,
            @RequestParam(required = false) String date_to) {
        return ResponseEntity.ok(camaraService.getEvents(limit, date_from, date_to));
    }

    @GetMapping("/stats")
    public ResponseEntity<CamaraStatsResponseDto> stats(
            @RequestParam(defaultValue = "288") int limit,
            @RequestParam(required = false) String date_from,
            @RequestParam(required = false) String date_to) {
        return ResponseEntity.ok(camaraService.getStats(limit, date_from, date_to));
    }
}
