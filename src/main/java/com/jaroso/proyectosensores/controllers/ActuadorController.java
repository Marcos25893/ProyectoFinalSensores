package com.jaroso.proyectosensores.controllers;

import com.jaroso.proyectosensores.dto.ActuatorActionDto;
import com.jaroso.proyectosensores.dto.SensorDecisionResponseDto;
import com.jaroso.proyectosensores.services.GestionRiegoService;
import com.jaroso.proyectosensores.services.ModoHumedadService;
import com.jaroso.proyectosensores.services.ModoNivelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/automatizar")
public class ActuadorController {

    @Autowired
    private GestionRiegoService gestionRiegoService;

    @Autowired
    private ModoNivelService modoNivelService;

    @Autowired
    private ModoHumedadService modoHumedadService;

    @PostMapping("/actuadores")
    public ResponseEntity<SensorDecisionResponseDto> controlarActuador(@RequestBody ActuatorActionDto peticion) {
        SensorDecisionResponseDto respuesta =
                gestionRiegoService.procesarCambioEstado(peticion.id(), peticion.estado());

        if (!respuesta.permiso()) {
            return ResponseEntity.badRequest().body(respuesta);
        }
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/modo/nivel")
    public ResponseEntity<Boolean> obtenerModoNivel() {
        return ResponseEntity.ok(modoNivelService.isAutomatico());
    }

    @PostMapping("/modo/nivel")
    public void cambiarModoNivel(@RequestParam boolean automatico) {
        modoNivelService.setModoAutomatico(automatico);
    }

    @GetMapping("/modo/humedad")
    public ResponseEntity<Boolean> obtenerModoHumedad() {
        return ResponseEntity.ok(modoHumedadService.isAutomatico());
    }

    @PostMapping("/modo/humedad")
    public void cambiarModoHumedad(@RequestParam boolean automatico) {
        modoHumedadService.setModoAutomatico(automatico);
    }
}
