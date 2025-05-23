// Verification.java
package com.umg.biometric;

import com.umg.modelos.EmpleadoAsistencia;
import com.umg.sql.FingerprintDAO;
import com.umg.message.MessageBox;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.digitalpersona.uareu.*;

public class Verification
		extends JPanel
		implements ActionListener
{
	private static final long serialVersionUID = 6;
	private static final String ACT_BACK = "back";

	private CaptureThread m_capture;
	private Reader m_reader;
	private JDialog m_dlgParent;
	private JTextArea m_text;

	private final String m_strPrompt1 = "Verification started\n    put any finger on the reader\n\n";

	private Verification(Reader reader) {
		m_reader = reader;

		final int vgap = 5;
		final int width = 380;

		BoxLayout layout = new BoxLayout(this, BoxLayout.Y_AXIS);
		setLayout(layout);

		m_text = new JTextArea(22, 1);
		m_text.setEditable(false);
		JScrollPane paneReader = new JScrollPane(m_text);
		add(paneReader);
		Dimension dm = paneReader.getPreferredSize();
		dm.width = width;
		paneReader.setPreferredSize(dm);

		add(Box.createVerticalStrut(vgap));

		JButton btnBack = new JButton("Back");
		btnBack.setActionCommand(ACT_BACK);
		btnBack.addActionListener(this);
		add(btnBack);
		add(Box.createVerticalStrut(vgap));

		setOpaque(true);
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getActionCommand().equals(ACT_BACK)) {
			StopCaptureThread();
		} else if (e.getActionCommand().equals(CaptureThread.ACT_CAPTURE)) {
			CaptureThread.CaptureEvent evt = (CaptureThread.CaptureEvent) e;
			if (ProcessCaptureResult(evt)) {
				WaitForCaptureThread();
				StartCaptureThread();
			} else {
				m_dlgParent.setVisible(false);
			}
		}
	}

	private void StartCaptureThread() {
		m_capture = new CaptureThread(m_reader, false, Fid.Format.ANSI_381_2004, Reader.ImageProcessing.IMG_PROC_DEFAULT);
		m_capture.start(this);
	}

	private void StopCaptureThread() {
		if (m_capture != null) m_capture.cancel();
	}

	private void WaitForCaptureThread() {
		if (m_capture != null) m_capture.join(1000);
	}

	private boolean ProcessCaptureResult(CaptureThread.CaptureEvent evt) {
		boolean bCanceled = false;

		if (evt.capture_result != null) {
			if (evt.capture_result.image != null && Reader.CaptureQuality.GOOD == evt.capture_result.quality) {
				Engine engine = UareUGlobal.GetEngine();

				try {
					Fmd probe = engine.CreateFmd(evt.capture_result.image, Fmd.Format.ANSI_378_2004);

					// Buscar huella en base de datos
					//EmpleadoAsistencia empleado = new FingerprintDAO().validateFingerprint(probe);
					EmpleadoAsistencia empleado = null;
					if (empleado != null) {
						m_text.append("✅ Huella reconocida. Empleado ID: " + empleado.getId() + "\n\n");
					} else {
						m_text.append("⚠️ Huella no reconocida.\n\n");
					}


					m_text.append(m_strPrompt1);

				} catch (UareUException e) {
					MessageBox.DpError("Engine.CreateFmd()", e);
				}

			} else if (Reader.CaptureQuality.CANCELED == evt.capture_result.quality) {
				bCanceled = true;
			} else {
				MessageBox.BadQuality(evt.capture_result.quality);
			}
		} else if (evt.exception != null) {
			MessageBox.DpError("Capture", evt.exception);
			bCanceled = true;
		} else if (evt.reader_status != null) {
			MessageBox.BadStatus(evt.reader_status);
			bCanceled = true;
		}

		return !bCanceled;
	}

	private void doModal(JDialog dlgParent) {
		try {
			m_reader.Open(Reader.Priority.COOPERATIVE);
		} catch (UareUException e) {
			MessageBox.DpError("Reader.Open()", e);
		}

		StartCaptureThread();
		m_text.append(m_strPrompt1);

		m_dlgParent = dlgParent;
		m_dlgParent.setContentPane(this);
		m_dlgParent.pack();
		m_dlgParent.setLocationRelativeTo(null);
		m_dlgParent.toFront();
		m_dlgParent.setVisible(true);
		m_dlgParent.dispose();

		StopCaptureThread();
		WaitForCaptureThread();

		try {
			m_reader.Close();
		} catch (UareUException e) {
			MessageBox.DpError("Reader.Close()", e);
		}
	}

	public static void Run(Reader reader) {
		JDialog dlg = new JDialog((JDialog) null, "Verification", true);
		Verification verification = new Verification(reader);
		verification.doModal(dlg);
	}
}
