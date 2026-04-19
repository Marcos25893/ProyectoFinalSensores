package com.jaroso.proyectosensores.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sectores")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Sector {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    private String nombre;

    private String cultivo;

    private String parcela;

    private Double superficie;

    private Double latitud;

    private Double longitud;
}