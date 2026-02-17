package com.jaroso.proyectosensores.repositories;

import com.jaroso.proyectosensores.entities.Lectura;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LecturaRepository extends JpaRepository<Lectura, Long> {
}
