package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.ActuatorActionDto;
import com.jaroso.proyectosensores.dto.SensorDecisionResponseDto;
import com.jaroso.proyectosensores.services.GestionRiegoService;
import com.jaroso.proyectosensores.services.ModoRiegoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/automatizar")
public class ActuadorController {

    @Autowired
    private GestionRiegoService gestionRiegoService;

    @Autowired
    private ModoRiegoService modoRiegoService;

    @PostMapping("/actuadores")
    public ResponseEntity<SensorDecisionResponseDto> controlarActuador(@RequestBody ActuatorActionDto peticion) {
        SensorDecisionResponseDto respuesta =
                gestionRiegoService.procesarCambioEstado(peticion.id(), peticion.estado());

        if (!respuesta.permiso()) {
            return ResponseEntity.badRequest().body(respuesta);
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/modo")
    public ResponseEntity<Boolean> obtenerModo() {
        return ResponseEntity.ok(modoRiegoService.isAutomatico());
    }

    @PostMapping("/modo")
    public void cambiarModo(@RequestParam boolean automatico) {
        modoRiegoService.setModoAutomatico(automatico);
    }
}
