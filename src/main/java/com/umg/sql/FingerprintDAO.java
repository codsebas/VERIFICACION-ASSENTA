package com.umg.sql;

import com.umg.modelos.EmpleadoAsistencia;
import com.umg.message.MessageBox;

import java.sql.*;

import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUGlobal;
import com.digitalpersona.uareu.UareUException;

public class FingerprintDAO {

    private static final int THRESHOLD = Engine.PROBABILITY_ONE / 100_000;

    public EmpleadoAsistencia validateFingerprint(Fmd probeTemplate) {
        String sql    = "SELECT empleado_id, huella_template FROM huella";
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
                    byte[] data    = rs.getBytes("huella_template");

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

        String sql = "SELECT id_empleado, dpi_empleado, nombre1_empleado, nombre2_empleado, " +
                "apellido1_empleado, apellido2_empleado, horario_entrada, horario_salida " +
                "FROM empleado WHERE id_empleado = ?";

        try (Connection conn = PostgresConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, empleadoId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String nombreCompleto = rs.getString("nombre1_empleado") + " " +
                            rs.getString("nombre2_empleado") + " " +
                            rs.getString("apellido1_empleado") + " " +
                            rs.getString("apellido2_empleado");

                    empleado = new EmpleadoAsistencia();
                    empleado.setId(rs.getInt("id_empleado"));
                    empleado.setDpi(rs.getString("dpi_empleado"));
                    empleado.setNombreCompleto(nombreCompleto.trim());
                    empleado.setHoraEntrada(rs.getTime("horario_entrada").toString());
                    empleado.setHoraSalida(rs.getTime("horario_salida").toString());
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Error al consultar empleado: " + e.getMessage());
        }

        return empleado;
    }

    public boolean registrarAsistencia(int idEmpleado, String fechaHoy, String horaActual) {
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
                System.err.println("❌ No se pudo establecer conexión a la base de datos.");
                return false;
            }

            // Buscar si ya hay registro para hoy
            try (PreparedStatement stmtBuscar = conn.prepareStatement(sqlBuscar)) {
                stmtBuscar.setDate(1, Date.valueOf(fechaHoy)); // fecha_asistencia
                stmtBuscar.setInt(2, idEmpleado); // empleado_id

                try (ResultSet rs = stmtBuscar.executeQuery()) {
                    if (rs.next()) {
                        // Ya existe registro para hoy
                        Time horaEntrada = rs.getTime("hora_entrada");
                        Time horaSalida = rs.getTime("hora_salida");

                        if (horaEntrada != null && horaSalida == null) {
                            // Tiene hora_entrada, falta hora_salida → hacemos UPDATE
                            try (PreparedStatement stmtUpdate = conn.prepareStatement(sqlUpdate)) {
                                stmtUpdate.setTime(1, Time.valueOf(horaActual)); // Nueva hora_salida
                                stmtUpdate.setDate(2, Date.valueOf(fechaHoy));
                                stmtUpdate.setInt(3, idEmpleado);
                                stmtUpdate.executeUpdate();
                                System.out.println("✅ Hora de salida registrada para empleado ID: " + idEmpleado);
                                return true;
                            }
                        } else if (horaEntrada != null && horaSalida != null) {
                            // Ya tiene entrada y salida
                            System.out.println("⚠️ Ya se registró entrada y salida para hoy. No se puede registrar más.");
                            return false;
                        }
                    }
                }
            }

            // No existe registro → hacemos INSERT
            try (PreparedStatement stmtInsert = conn.prepareStatement(sqlInsert)) {
                stmtInsert.setDate(1, Date.valueOf(fechaHoy)); // fecha_asistencia
                stmtInsert.setInt(2, idEmpleado);              // empleado_id
                stmtInsert.setTime(3, Time.valueOf(horaActual)); // hora_entrada
                stmtInsert.executeUpdate();
                System.out.println("✅ Hora de entrada registrada para empleado ID: " + idEmpleado);
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error al registrar asistencia: " + e.getMessage());
            return false;
        }
    }


}
