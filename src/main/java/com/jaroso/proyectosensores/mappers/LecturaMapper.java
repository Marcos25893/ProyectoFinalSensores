package com.jaroso.proyectosensores.mappers;

import com.jaroso.proyectosensores.dto.LecturaCreateDto;
import com.jaroso.proyectosensores.dto.LecturaDto;
import com.jaroso.proyectosensores.entities.Lectura;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LecturaMapper {

    @Mapping(source = "sensor.id", target = "sensorId")
    LecturaDto toDto (Lectura lectura);

    @Mapping(target = "sensor", ignore = true)
    Lectura toEntity (LecturaCreateDto lecturaDto);

}
