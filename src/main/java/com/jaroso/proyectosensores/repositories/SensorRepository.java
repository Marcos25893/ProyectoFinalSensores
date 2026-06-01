package com.jaroso.proyectosensores.repositories;

import com.jaroso.proyectosensores.entities.Sensor;
import com.jaroso.proyectosensores.entities.TipoSensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {
    List<Sensor> findSensoresBySectorId(Long sectorId);
    List<Sensor> findBySectorIdAndTipo(Long sectorId, TipoSensor tipo);
    Optional<Sensor> findFirstBySectorIdAndTipo(Long sectorId, TipoSensor tipo);
    Optional<Sensor> findByNombre(String nombre);
}
