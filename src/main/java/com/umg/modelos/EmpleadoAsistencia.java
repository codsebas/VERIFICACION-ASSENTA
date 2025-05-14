package com.umg.modelos;

public class EmpleadoAsistencia {
    private int id;
    private String dpi;
    private String nombreCompleto;
    private String horaEntrada;
    private String horaSalida;

    public EmpleadoAsistencia() {}

    public EmpleadoAsistencia(int id, String dpi, String nombreCompleto, String horaEntrada, String horaSalida) {
        this.id = id;
        this.dpi = dpi;
        this.nombreCompleto = nombreCompleto;
        this.horaEntrada = horaEntrada;
        this.horaSalida = horaSalida;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDpi() { return dpi; }
    public void setDpi(String dpi) { this.dpi = dpi; }

    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }

    public String getHoraEntrada() { return horaEntrada; }
    public void setHoraEntrada(String horaEntrada) { this.horaEntrada = horaEntrada; }

    public String getHoraSalida() { return horaSalida; }
    public void setHoraSalida(String horaSalida) { this.horaSalida = horaSalida; }
}
