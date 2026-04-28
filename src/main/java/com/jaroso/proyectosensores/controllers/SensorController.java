package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.SensorCreateDto;
import com.jaroso.proyectosensores.dto.SensorDto;
import com.jaroso.proyectosensores.dto.SensorUpdateDto;
import com.jaroso.proyectosensores.entities.Sector;
import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.mappers.SensorMapper;
import com.jaroso.proyectosensores.repositories.SectorRepository;
import com.jaroso.proyectosensores.repositories.SensorRepository;
import com.jaroso.proyectosensores.services.MqttService;
import java.util.logging.Logger;
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
    private SectorRepository SectorRepository;

    @Autowired
    private SensorMapper mapper;
    @Autowired
    private MqttService mqttService;

    @GetMapping
    public ResponseEntity<List<SensorDto>> getAllSensor(){
        return ResponseEntity.ok(SensorRepository.findAll().stream().map(mapper::toDto).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SensorDto> getSensorById(@PathVariable Long id){
        Optional<SensorDto> Sensor = SensorRepository.findById(id).map(mapper::toDto);
        return Sensor.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/bySectorId/{SectorId}")
    public ResponseEntity<List<SensorDto>> findSensorsBySectorId(@PathVariable Long SectorId) {
        List<SensorDto> sensors = SensorRepository.findSensoresBySectorId(SectorId).stream().map(mapper::toDto).toList();
        return ResponseEntity.ok(sensors);
    }

    @PostMapping
    public ResponseEntity<SensorDto> createSensor(@RequestBody SensorCreateDto sensor){
       Optional<Sector> sector = SectorRepository.findById(sensor.sectorId());
       if (sector.isPresent()) {
              Sensor sensorEntity = mapper.toEntity(sensor);
              sensorEntity.setSector(sector.get());
              return ResponseEntity.status(HttpStatus.CREATED)
                     .body(mapper.toDto(SensorRepository.save(sensorEntity)));
         } else {
              return ResponseEntity.badRequest().build();
       }
    }

    @PutMapping("/sensores/{id}")
    public ResponseEntity<SensorDto> updateSensor(@PathVariable Long id, @RequestBody SensorUpdateDto sensorUpdateDto){
        Optional<Sensor> sensor = SensorRepository.findById(id);
        if (sensor.isPresent()){
            sensor.get().setEstado(sensorUpdateDto.estado());

            // publica un mensaje MQTT al topic del actuador (ej: actuadores/1/comando con payload ON o OFF)
            String payload = String.format("{\"estado\": \"%s\"}", sensorUpdateDto.estado());
            mqttService.publish("iot/sensor/" +
                    sensor.get().getId() + "/", payload);

            return ResponseEntity.ok(mapper.toDto(SensorRepository.save(sensor.get())));
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
