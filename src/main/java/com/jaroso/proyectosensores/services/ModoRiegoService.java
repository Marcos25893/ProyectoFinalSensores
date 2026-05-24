package com.jaroso.proyectosensores.services;

import org.springframework.stereotype.Service;

@Service
public class ModoRiegoService {

    private boolean modoAutomatico = true;

    public boolean isAutomatico() {
        return this.modoAutomatico;
    }

    public void setModoAutomatico(boolean nuevoModo) {
        this.modoAutomatico = nuevoModo;
    }
}
