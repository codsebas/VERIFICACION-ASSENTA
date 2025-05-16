package com.umg.modelos;

public class ModeloResultadoAsistencia {

    private boolean estatus;
    private String mensaje;

    public ModeloResultadoAsistencia(boolean estatus, String mensaje) {
        this.estatus = estatus;
        this.mensaje = mensaje;
    }

    public boolean isEstatus() {
        return estatus;
    }

    public void setEstatus(boolean estatus) {
        this.estatus = estatus;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }
}
