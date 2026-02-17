package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.LecturaCreateDto;
import com.jaroso.proyectosensores.dto.LecturaDto;
import com.jaroso.proyectosensores.dto.SensorCreateDto;
import com.jaroso.proyectosensores.dto.SensorDto;
import com.jaroso.proyectosensores.entities.Lectura;
import com.jaroso.proyectosensores.mappers.LecturaMapper;
import com.jaroso.proyectosensores.repositories.LecturaRepository;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(lecturaRepository.save(mapper.toEntity(lectura))));
    }










}
