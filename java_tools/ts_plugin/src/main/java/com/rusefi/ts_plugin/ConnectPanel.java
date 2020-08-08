package com.rusefi.ts_plugin;

import com.devexperts.logging.Logging;
import com.rusefi.autodetect.PortDetector;
import com.rusefi.io.ConnectionStateListener;
import com.rusefi.io.LinkManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.devexperts.logging.Logging.getLogging;

/**
 * todo: move IO away from AWT thread
 */
public class ConnectPanel {
    private static final Logging log = getLogging(ConnectPanel.class);
    static final Executor IO_THREAD = Executors.newSingleThreadExecutor();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel status = new JLabel();

    private LinkManager controllerConnector;
    private final JButton connect = new JButton("Connect");
    private final JButton disconnect = new JButton("Disconnect");

    public ConnectPanel(final ConnectionStateListener connectionStateListener) {
        JPanel flow = new JPanel(new FlowLayout());

        disconnect.setEnabled(false);
        disconnect.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                controllerConnector.close();
                status.setText("Disconnected");
                disconnect.setEnabled(false);
                connect.setEnabled(true);
            }
        });

        connect.addActionListener(e -> {
            connect.setEnabled(false);
            status.setText("Looking for rusEFI...");

            IO_THREAD.execute(() -> {
                controllerConnector = new LinkManager()
                        .setCompositeLogicEnabled(false)
                        .setNeedPullData(false);
                try {
                    tryToConnect(connectionStateListener);
                } catch (Throwable er) {
                    log.error("Error connecting", er);
                    SwingUtilities.invokeLater(() -> {
                        status.setText("Some error, see logs.");
                        connect.setEnabled(true);
                    });
                }
            });
        });

        flow.add(connect);
        flow.add(disconnect);

        content.add(flow, BorderLayout.NORTH);
        content.add(status, BorderLayout.SOUTH);
    }

    private void tryToConnect(ConnectionStateListener connectionStateListener) {
        String autoDetectedPort = PortDetector.autoDetectSerial(null);
        if (autoDetectedPort == null) {
            status.setText("rusEFI not found");
            connect.setEnabled(true);
        } else {
            controllerConnector.startAndConnect(autoDetectedPort, new ConnectionStateListener() {
                public void onConnectionEstablished() {
                    SwingUtilities.invokeLater(() -> {
                        status.setText("Connected to rusEFI");
                        disconnect.setEnabled(true);
                        connectionStateListener.onConnectionEstablished();
                    });
                }

                public void onConnectionFailed() {
                }
            });
        }
    }

    public LinkManager getControllerConnector() {
        return controllerConnector;
    }

    public static String getLastFour(String fileName) {
        int dotIndex = fileName.indexOf(".");
        fileName = fileName.substring(0, dotIndex);
        if (fileName.length() < 5)
            return fileName;
        return fileName.substring(fileName.length() - 4);
    }

    public JComponent getContent() {
        return content;
    }
}
