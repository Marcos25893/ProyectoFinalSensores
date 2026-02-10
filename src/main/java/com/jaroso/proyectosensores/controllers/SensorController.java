package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.SensorCreateDto;
import com.jaroso.proyectosensores.dto.SensorDto;
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

    @PostMapping
    public ResponseEntity<SensorDto> createSensor(@RequestBody SensorCreateDto sensor){
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(SensorRepository.save(mapper.toEntity(sensor))));
    }


}
