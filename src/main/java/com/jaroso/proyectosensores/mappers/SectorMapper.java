package com.jaroso.proyectosensores.mappers;

import com.jaroso.proyectosensores.dto.SectorCreateDto;
import com.jaroso.proyectosensores.dto.SectorDto;
import com.jaroso.proyectosensores.entities.Sector;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SectorMapper {
    SectorDto toDto(Sector sector);

    Sector toEntity(SectorCreateDto sectorCreateDto);
}
