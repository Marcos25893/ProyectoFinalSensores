package com.jaroso.proyectosensores.mappers;

import com.jaroso.proyectosensores.dto.SensorCreateDto;
import com.jaroso.proyectosensores.dto.SensorDto;
import com.jaroso.proyectosensores.entities.Sensor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")

public interface SensorMapper {
    @Mapping(source = "sector.id", target = "sectorId")
    SensorDto toDto (Sensor sensor);

    @Mapping(target = "sector", ignore = true)
    Sensor toEntity (SensorCreateDto sensorDto);
}
