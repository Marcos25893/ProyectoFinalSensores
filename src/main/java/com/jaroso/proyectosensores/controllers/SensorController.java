package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.SensorCreateDto;
import com.jaroso.proyectosensores.dto.SensorDto;
import com.jaroso.proyectosensores.dto.SensorUpdateDto;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.mappers.SensorMapper;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/sensor")
public class SensorController {

    @Autowired
    private SensorRepository SensorRepository;

    @Autowired
    private SensorMapper mapper;

    @GetMapping
    public ResponseEntity<List<SensorDto>> getAllSensor(){
        return ResponseEntity.ok(SensorRepository.findAll().stream().map(mapper::toDto).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SensorDto> getSensorById(@PathVariable Long id){
        Optional<SensorDto> Sensor = SensorRepository.findById(id).map(mapper::toDto);
        return Sensor.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SensorDto> createSensor(@RequestBody SensorCreateDto sensor){
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(SensorRepository.save(mapper.toEntity(sensor))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SensorDto> updateSensor(@PathVariable Long id, @RequestBody SensorUpdateDto sensor){
        Optional<Sensor> sensores = SensorRepository.findById(id);
        if (sensores.isPresent()){
            sensores.get().setEstado(SensorUpdateDto.);
            return ResponseEntity.ok(mapper.toDto(SensorRepository.save(sensores.get())));
        } else {
            return ResponseEntity.notFound().build();
        }
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSensor(@PathVariable Long id){
        if (SensorRepository.existsById(id)) {
            SensorRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

}
