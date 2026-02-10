package com.jaroso.proyectosensores.entities;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

@Entity(name = "sensor")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Sensor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    private String nombre;

    private String description;

    private String sector;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoSensor tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSensor estado;
/*
    @Column(columnDefinition = "POINT")
    private Point location;*/

}
