package com.jaroso.proyectosensores.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity(name = "lecturas")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Lectura {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sensor_id", nullable = false)
    private Sensor sensor;

    private Double valor;

    private String unidad;

    private LocalDateTime timestamp;





}
