package com.jaroso.proyectosensores.mappers;

import com.jaroso.proyectosensores.dto.SensorCreateDto;
import com.jaroso.proyectosensores.dto.SensorDto;
import com.jaroso.proyectosensores.entities.Sensor;
import org.mapstruct.Mapper;
import org.springframework.web.bind.annotation.Mapping;

@Mapper(componentModel = "spring")
public interface SensorMapper {

    SensorDto toDto (Sensor sensor);
    Sensor toEntity (SensorCreateDto sensorDto);
}
