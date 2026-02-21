package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.LecturaCreateDto;
import com.jaroso.proyectosensores.dto.LecturaDto;
import com.jaroso.proyectosensores.entities.Lectura;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.mappers.LecturaMapper;
import com.jaroso.proyectosensores.repositories.LecturaRepository;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("lectura")
public class LecturaController {

    @Autowired
    private LecturaRepository lecturaRepository;

    @Autowired
    private LecturaMapper mapper;

    @Autowired
    private SensorRepository sensorRepository;

    @GetMapping
    public ResponseEntity<List<LecturaDto>> getAllLecturas() {
        return ResponseEntity.ok(lecturaRepository.findAll().stream().map(mapper::toDto).toList());
    }

    @PostMapping
    public ResponseEntity<LecturaDto> createLectura(@RequestBody LecturaCreateDto lectura){
        Optional<Sensor> sensor = sensorRepository.findById(lectura.sensorId());


        if (sensor.isPresent()) {
            Lectura nuevaLectura = mapper.toEntity(lectura);
            nuevaLectura.setSensor(sensor.get());
            nuevaLectura.setTimestamp(LocalDateTime.now());
            Lectura guardada = lecturaRepository.save(nuevaLectura);
            return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(guardada));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/bySensorId/{sensorId}")
    public ResponseEntity<List<LecturaDto>> findLecturasBySensorId(@PathVariable Long sensorId) {
        List<LecturaDto> lecturas = lecturaRepository.findAll().stream()
                .filter(lectura -> lectura.getSensor().getId().equals(sensorId))
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(lecturas);
    }

        @GetMapping("/bySensorIdAndFecha")
    public ResponseEntity<List<LecturaDto>> findLecturasBySensorIdAndFecha(
            @RequestBody Long sensorId,
            @RequestBody LocalDateTime fechaDesde,
            @RequestBody LocalDateTime fechaHasta) {
        List<LecturaDto> lecturas = lecturaRepository.findAll().stream()
                .filter(lectura -> lectura.getSensor().getId().equals(sensorId) &&
                        lectura.getTimestamp().isAfter(fechaDesde) &&
                        lectura.getTimestamp().isBefore(fechaHasta))
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(lecturas);
        }










}
