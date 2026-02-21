package com.jaroso.proyectosensores.repositories;

import com.jaroso.proyectosensores.entities.Lectura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LecturaRepository extends JpaRepository<Lectura, Long> {

    List<Lectura> findLecturasBySensorId(Long sensorId);

    List<Lectura> findLecturasBySensorIdAndTimestampBetween(Long sensorId, LocalDateTime fechaDesde, LocalDateTime fechaHasta);
}
