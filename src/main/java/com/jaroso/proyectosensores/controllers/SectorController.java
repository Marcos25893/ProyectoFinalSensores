package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.SectorCreateDto;
import com.jaroso.proyectosensores.dto.SectorDto;
import com.jaroso.proyectosensores.entities.Sector;
import com.jaroso.proyectosensores.mappers.SectorMapper;
import com.jaroso.proyectosensores.repositories.SectorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
public class SectorController {
    @Autowired
    private SectorRepository SectorRepository;

    @Autowired
    private SectorMapper mapper;

    @GetMapping("/sectores")
    public ResponseEntity<List<SectorDto>> getAll(){
        return ResponseEntity.ok(SectorRepository.findAll().stream().map(mapper::toDto).toList());
    }

    @GetMapping("/sectores/{id}")
    public ResponseEntity<SectorDto> getById(@PathVariable Long id){
        Optional<SectorDto> sensor = SectorRepository.findById(id).map(mapper::toDto);
        return sensor.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/sectores")
    public ResponseEntity<SectorDto> createSensor(@RequestBody SectorCreateDto sector){
        Sector sectorEntity = mapper.toEntity(sector);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toDto(SectorRepository.save(sectorEntity)));
    }

    @DeleteMapping("/sectores/{id}")
    public ResponseEntity<Void> deleteSensor(@PathVariable Long id){
        SectorRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}
