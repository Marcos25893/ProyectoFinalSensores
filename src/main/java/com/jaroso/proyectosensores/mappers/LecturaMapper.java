package com.jaroso.proyectosensores.mappers;

import com.jaroso.proyectosensores.dto.LecturaCreateDto;
import com.jaroso.proyectosensores.dto.LecturaDto;
import com.jaroso.proyectosensores.entities.Lectura;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LecturaMapper {

    LecturaDto toDto (Lectura lectura);
     Lectura toEntity (LecturaCreateDto lecturaDto);

}
