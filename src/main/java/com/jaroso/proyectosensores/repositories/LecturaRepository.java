package com.jaroso.proyectosensores.repositories;

import com.jaroso.proyectosensores.entities.Lectura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LecturaRepository extends JpaRepository<Lectura, Long> {
}
