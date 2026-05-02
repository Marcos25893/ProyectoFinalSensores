package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.ActuatorActionDto;
import com.jaroso.proyectosensores.dto.SensorDecisionResponseDto;
import com.jaroso.proyectosensores.services.GestionRiegoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/automatizar/dispositivos")
public class ActuadorController {

    @Autowired
    private GestionRiegoService gestionRiegoService;

    @PostMapping("/controlar")
    public ResponseEntity<SensorDecisionResponseDto> controlarActuador(@RequestBody ActuatorActionDto peticion) {
        SensorDecisionResponseDto respuesta =
                gestionRiegoService.procesarCambioEstado(peticion.id(), peticion.estado());

        if (!respuesta.permiso()) {
            return ResponseEntity.badRequest().body(respuesta);
        }
        return ResponseEntity.ok(respuesta);
    }
}
