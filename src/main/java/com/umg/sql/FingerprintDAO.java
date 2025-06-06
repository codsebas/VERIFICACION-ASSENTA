package com.umg.sql;

import com.umg.modelos.EmpleadoAsistencia;
import com.umg.message.MessageBox;

import java.sql.*;
import java.time.LocalDate;

import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUGlobal;
import com.digitalpersona.uareu.UareUException;
import com.umg.modelos.ModeloResultadoAsistencia;

public class FingerprintDAO {

    private static final int THRESHOLD = Engine.PROBABILITY_ONE / 100_000;

    public EmpleadoAsistencia validateFingerprint(Fmd probeTemplate) {
        String sql =
                "SELECT h.empleado_id, h.huella_template " +
                        "FROM huella h " +
                        "INNER JOIN empleado e " +
                        "  ON h.empleado_id = e.id_empleado " +
                        "WHERE e.estado_empleado = 'A'";
        Engine engine = UareUGlobal.GetEngine();
        EmpleadoAsistencia empleado = null;

        try (Connection conn = PostgresConnection.getConnection()) {

            if (conn == null) {
                System.err.println("❌ No se pudo establecer conexión a la base de datos.");
                return null;
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int empleadoId = rs.getInt("empleado_id");
                    byte[] data = rs.getBytes("huella_template");

                    // Reconstruir Fmd desde bytes
                    Fmd candidate = UareUGlobal.GetImporter()
                            .ImportFmd(data,
                                    Fmd.Format.ANSI_378_2004,
                                    Fmd.Format.ANSI_378_2004);

                    // Comparar
                    int score = engine.Compare(probeTemplate, 0, candidate, 0);
                    if (score < THRESHOLD) {
                        System.out.println("✅ Huella coincide con empleado ID: " + empleadoId);
                        empleado = devolverDatos(empleadoId);
                        return empleado;
                    }
                }

            }

        } catch (SQLException e) {
            System.err.println("❌ Error al leer huellas de DB: " + e.getMessage());
        } catch (UareUException e) {
            System.err.println("❌ Error en el motor biométrico: " + e.getMessage());
        }

        System.out.println("⚠️ No se encontró coincidencia de huella.");
        return null;
    }

    private EmpleadoAsistencia devolverDatos(int empleadoId) {
        EmpleadoAsistencia empleado = null;
        LocalDate fechaHoy = LocalDate.now();

        String sqlEmpleado = """
        SELECT 
            id_empleado, dpi_empleado, nombre1_empleado, nombre2_empleado, nombre3_empleado,
            apellido1_empleado, apellido2_empleado, apellidocasada_empleado
        FROM empleado
        WHERE id_empleado = ?;
    """;

        String sqlAsistencia = """
        SELECT hora_entrada, hora_salida
        FROM asistencia_diaria
        WHERE empleado_id = ? AND fecha_asistencia = ?
        ORDER BY correlativo_asistencia DESC
        LIMIT 1;
    """;

        try (Connection conn = PostgresConnection.getConnection()) {
            if (conn == null) {
                System.err.println("❌ Conexión fallida.");
                return null;
            }

            // Query 1: Datos del empleado
            try (PreparedStatement stmtEmpleado = conn.prepareStatement(sqlEmpleado)) {
                stmtEmpleado.setInt(1, empleadoId);

                try (ResultSet rs = stmtEmpleado.executeQuery()) {
                    if (rs.next()) {
                        String nombreCompleto = safeConcat(
                                rs.getString("nombre1_empleado"),
                                rs.getString("nombre2_empleado"),
                                rs.getString("nombre3_empleado"),
                                rs.getString("apellido1_empleado"),
                                rs.getString("apellido2_empleado"),
                                rs.getString("apellidocasada_empleado")
                        );

                        empleado = new EmpleadoAsistencia();
                        empleado.setId(rs.getInt("id_empleado"));
                        empleado.setDpi(rs.getString("dpi_empleado"));
                        empleado.setNombreCompleto(nombreCompleto.trim());
                    }
                }
            }

            // Si encontramos el empleado, intentamos traer su asistencia
            if (empleado != null) {
                try (PreparedStatement stmtAsistencia = conn.prepareStatement(sqlAsistencia)) {
                    stmtAsistencia.setInt(1, empleadoId);
                    stmtAsistencia.setDate(2, Date.valueOf(fechaHoy));

                    try (ResultSet rs = stmtAsistencia.executeQuery()) {
                        if (rs.next()) {
                            Time entrada = rs.getTime("hora_entrada");
                            Time salida = rs.getTime("hora_salida");
                            empleado.setHoraEntrada(entrada != null ? entrada.toString() : "");
                            empleado.setHoraSalida(salida != null ? salida.toString() : "");
                        } else {
                            empleado.setHoraEntrada("");
                            empleado.setHoraSalida("");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Error en DB: " + e.getMessage());
        }

        return empleado;
    }


    public ModeloResultadoAsistencia registrarAsistencia(int idEmpleado, String fechaHoy, String horaActual) {
        String sqlBuscar = """
                SELECT fecha_asistencia, correlativo_asistencia, hora_entrada, hora_salida
                FROM asistencia_diaria
                WHERE fecha_asistencia = ? AND empleado_id = ?
                ORDER BY correlativo_asistencia DESC
                LIMIT 1;
                """;

        String sqlInsert = """
                INSERT INTO asistencia_diaria (fecha_asistencia, empleado_id, hora_entrada, hora_salida)
                VALUES (?, ?, ?, NULL);
                """;

        String sqlUpdate = """
                UPDATE asistencia_diaria
                SET hora_salida = ?
                WHERE fecha_asistencia = ? AND empleado_id = ? AND hora_salida IS NULL;
                """;

        try (Connection conn = PostgresConnection.getConnection()) {

            if (conn == null) {
                return new ModeloResultadoAsistencia(false, "E", "No se pudo establecer conexión con la base de datos.");
            }

            try (PreparedStatement stmtBuscar = conn.prepareStatement(sqlBuscar)) {
                stmtBuscar.setDate(1, Date.valueOf(fechaHoy));
                stmtBuscar.setInt(2, idEmpleado);

                try (ResultSet rs = stmtBuscar.executeQuery()) {
                    if (rs.next()) {
                        Time horaEntrada = rs.getTime("hora_entrada");
                        Time horaSalida = rs.getTime("hora_salida");

                        if (horaEntrada != null && horaSalida == null) {
                            try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                                stmtUpdate.setTime(1, Time.valueOf(horaActual));
                                stmtUpdate.setDate(2, Date.valueOf(fechaHoy));
                                stmtUpdate.setInt(3, idEmpleado);
                                stmtUpdate.executeUpdate();
                                return new ModeloResultadoAsistencia(true, "S", "Hora de salida registrada correctamente. Buen retorno a casa.");
                            }
                        } else if (horaEntrada != null && horaSalida != null) {
                            return new ModeloResultadoAsistencia(false, "E", "Ya se ha registrado entrada y salida para hoy.");
                        }
                    }
                }
            }

            // No existe registro, insertar
            try (PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert)) {
                stmtInsert.setDate(1, Date.valueOf(fechaHoy));
                stmtInsert.setInt(2, idEmpleado);
                stmtInsert.setTime(3, Time.valueOf(horaActual));
                stmtInsert.executeUpdate();
                return new ModeloResultadoAsistencia(true, "A", "Hora de entrada registrada correctamente. No olvides registrar tu salida.");
            }

        } catch (SQLException e) {
            return new ModeloResultadoAsistencia(false, "E", "Error al registrar asistencia: " + e.getMessage());
        }
    }

    private String safeConcat(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String parte : partes) {
            if (parte != null && !parte.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(parte.trim());
            }
        }
        return sb.toString();
    }


}
