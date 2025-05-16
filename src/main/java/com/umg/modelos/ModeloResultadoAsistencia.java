package com.umg.modelos;

public class ModeloResultadoAsistencia {

    private boolean estatus;
    private String estado;
    private String mensaje;

    public ModeloResultadoAsistencia(boolean estatus, String estado, String mensaje) {
        this.estatus = estatus;
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public boolean isEstatus() {
        return estatus;
    }

    public String getEstado() {
        return estado;
    }

    public String getMensaje() {
        return mensaje;
    }
}
