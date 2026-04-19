package com.jaroso.proyectosensores.repositories;

import com.jaroso.proyectosensores.entities.Sensor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SensorRepository extends JpaRepository<Sensor, Long> {
    List<Sensor> findSensoresBySectorId(Long sectorId);
}
