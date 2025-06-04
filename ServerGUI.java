import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public class ServerGUI extends JFrame {
    private JTextArea logArea;
    private JTextField sendToField, messageField;
    private JButton startButton, stopButton, sendButton;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private boolean isRunning = false;
    private ConcurrentHashMap<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();

    public ServerGUI() {
        setTitle("Java Server GUI");
        setSize(700, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        startButton.addActionListener(e -> startServer());
        stopButton.addActionListener(e -> stopServer());

        sendToField = new JTextField(10);
        messageField = new JTextField(30);
        sendButton = new JButton("Send to Client");
        sendButton.setEnabled(false);

        sendButton.addActionListener(e -> sendMessageToClient());

        JPanel controlPanel = new JPanel();
        controlPanel.add(startButton);
        controlPanel.add(stopButton);

        JPanel messagePanel = new JPanel();
        messagePanel.add(new JLabel("To:"));
        messagePanel.add(sendToField);
        messagePanel.add(new JLabel("Message:"));
        messagePanel.add(messageField);
        messagePanel.add(sendButton);

        add(scrollPane, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.NORTH);
        add(messagePanel, BorderLayout.SOUTH);
    }

    private void startServer() {
        int PORT = 12345;
        isRunning = true;
        pool = Executors.newFixedThreadPool(10);
        log("[SERVER] Starting on port " + PORT + "...");

        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        sendButton.setEnabled(true);

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                while (isRunning) {
                    Socket client = serverSocket.accept();
                    log("[CONNECT] New client: " + client.getInetAddress());
                    pool.execute(new ClientHandler(client));
                }
            } catch (IOException e) {
                if (isRunning) log("[ERROR] Server crashed: " + e.getMessage());
            }
        }).start();
    }

    private void notifyServerShutdown() {
        JSONObject shutdownMsg = new JSONObject();
        shutdownMsg.put("type", "shutdown");
        shutdownMsg.put("message", "Server is shutting down");

        java.util.List<PrintWriter> writers = new java.util.ArrayList<>(clientWriters.values());

        for (PrintWriter writer : writers) {
            try {
                writer.println(shutdownMsg.toString());
                writer.flush();
            } catch (Exception e) {
                log("[ERROR] Notifying client of shutdown: " + e.getMessage());
            }
        }
    }

    private void stopServer() {
        isRunning = false;
        try {
            notifyServerShutdown();
            if (serverSocket != null) serverSocket.close();
            if (pool != null) {
                pool.shutdown();
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            }
            log("[SERVER] Stopped.");
        } catch (IOException e) {
            log("[ERROR] Could not stop server: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("[ERROR] Server shutdown interrupted");
        }
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        sendButton.setEnabled(false);
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> logArea.append(message + "\n"));
    }

    private void sendMessageToClient() {
        String to = sendToField.getText().trim();
        String msg = messageField.getText().trim();

        if (msg.equalsIgnoreCase("disconnect")) {
            PrintWriter out = clientWriters.get(to);
            if (out != null) {
                JSONObject disconnectMsg = new JSONObject();
                disconnectMsg.put("type", "disconnect");
                disconnectMsg.put("message", "You have been disconnected by the server.");
                out.println(disconnectMsg.toString());
                out.flush();

                log("[SERVER] Disconnected client: " + to);
                clientWriters.remove(to);
            } else {
                log("[ERROR] Client not found: " + to);
            }
            return;
        }

        if (!to.isEmpty() && !msg.isEmpty()) {
            JSONObject json = new JSONObject();
            json.put("type", "message");
            json.put("from", "Server");
            json.put("message", msg);

            if ("all".equalsIgnoreCase(to)) {
                broadcastMessage(json);
                log("[SERVER → ALL] " + msg);
            } else {
                PrintWriter out = clientWriters.get(to);
                if (out != null) {
                    out.println(json.toString());
                    log("[SERVER → " + to + "] " + msg);
                } else {
                    log("[ERROR] Client not found: " + to);
                }
            }
            messageField.setText("");
        } else {
            log("[ERROR] Recipient and message cannot be empty.");
        }
    }

    private void broadcastMessage(JSONObject message) {
        java.util.List<PrintWriter> writers = new java.util.ArrayList<>(clientWriters.values());

        for (PrintWriter writer : writers) {
            writer.println(message.toString());
            writer.flush();
        }
    }

    private class ClientHandler implements Runnable {
        private Socket client;

        public ClientHandler(Socket socket) {
            this.client = socket;
        }

        @Override
        public void run() {
            String username = null;
            try (
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)
            ) {
                JSONObject welcomeMsg = new JSONObject();
                welcomeMsg.put("type", "welcome");
                welcomeMsg.put("message", "Connected to Java Server!");
                out.println(welcomeMsg.toString());

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    JSONObject receivedMsg = new JSONObject(inputLine);
                    String type = receivedMsg.optString("type");

                    if ("login".equalsIgnoreCase(type)) {
                        username = receivedMsg.optString("username", "");
                        if (!username.isEmpty()) {
                            clientWriters.put(username, out);
                            log("[LOGIN] " + username + " connected.");

                            JSONObject notify = new JSONObject();
                            notify.put("type", "notification");
                            notify.put("message", username + " has joined the chat");
                            broadcastMessage(notify);
                        }
                    } else if ("message".equalsIgnoreCase(type)) {
                        handleMessage(receivedMsg, out);
                    } else if ("command".equalsIgnoreCase(type)) {
                        handleCommand(receivedMsg, out);
                    } else if ("file_request".equalsIgnoreCase(type)) {
                        handleFileRequest(receivedMsg, out, in);
                    }
                }
            } catch (Exception e) {
                log("[ERROR] Client error: " + e.getMessage());
            } finally {
                if (username != null) {
                    synchronized (clientWriters) {
                        clientWriters.remove(username);
                    }
                    log("[DISCONNECT] " + username + " left.");

                    JSONObject userLeftMsg = new JSONObject();
                    userLeftMsg.put("type", "notification");
                    userLeftMsg.put("message", username + " has left the chat");
                    broadcastMessage(userLeftMsg);
                } else {
                    log("[DISCONNECT] Client left: " + client.getInetAddress());
                }

                try {
                    client.close();
                } catch (IOException e) {
                    log("[ERROR] Closing client: " + e.getMessage());
                }
            }
        }

        private void handleMessage(JSONObject msg, PrintWriter out) {
            String to = msg.optString("to", "all");
            String text = msg.optString("message", "");
            String from = msg.optString("username", "unknown");

            if (text.equalsIgnoreCase("ls")) {
                File dir = new File(".");
                String[] files = dir.list();
                StringBuilder response = new StringBuilder("Directory contents:\n");
                if (files != null) {
                    for (String file : files) response.append(file).append("\n");
                } else response.append("No files found.");

                JSONObject reply = new JSONObject();
                reply.put("type", "message");
                reply.put("from", "Server");
                reply.put("message", response.toString());
                out.println(reply.toString());
                out.flush();
                log("[SERVER → " + from + "] Sent directory listing.");
            } else if ("all".equalsIgnoreCase(to)) {
                JSONObject broadcast = new JSONObject();
                broadcast.put("type", "message");
                broadcast.put("from", from);
                broadcast.put("message", text);
                broadcastMessage(broadcast);
                log("[BROADCAST from " + from + "] " + text);
            } else {
                PrintWriter destOut = clientWriters.get(to);
                if (destOut != null) {
                    JSONObject direct = new JSONObject();
                    direct.put("type", "message");
                    direct.put("from", from);
                    direct.put("message", text);
                    destOut.println(direct.toString());
                    destOut.flush();
                    log("[MESSAGE from " + from + " → " + to + "] " + text);
                } else {
                    log("[ERROR] Client not found: " + to);
                }
            }
        }

        private void handleCommand(JSONObject msg, PrintWriter out) {
            String cmd = msg.optString("command", "");
            String from = msg.optString("username", "unknown");

            if (!cmd.isEmpty()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(System.getProperty("os.name").toLowerCase().contains("win") ?
                        new String[]{"cmd.exe", "/c", cmd} : new String[]{"bash", "-c", cmd});
                    pb.directory(new File("."));
                    Process process = pb.start();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) output.append(line).append("\n");
                    process.waitFor();

                    JSONObject result = new JSONObject();
                    result.put("type", "commandResult");
                    result.put("from", "Server");
                    result.put("result", output.toString().trim());

                    PrintWriter target = clientWriters.get(from);
                    if (target != null) target.println(result.toString());

                    log("[COMMAND from " + from + "] " + cmd);
                } catch (Exception e) {
                    log("[ERROR] Running command: " + e.getMessage());
                }
            }
        }

        private void handleFileRequest(JSONObject msg, PrintWriter out, BufferedReader in) {
            String fileName = msg.optString("filename", "");
            String from = msg.optString("username", "unknown");

            // Update this path to your desired file directory
            File file = new File("C:/Users/Muhammad Nassar/Desktop/cn project/server", fileName);

            if (!file.exists()) {
                try {
                    JSONObject errorMsg = new JSONObject();
                    errorMsg.put("type", "file_error");
                    errorMsg.put("message", "File not found: " + fileName);
                    out.println(errorMsg.toString());
                    out.flush();
                    log("[FILE ERROR] File not found: " + fileName);
                } catch (Exception e) {
                    log("[FILE ERROR] " + e.getMessage());
                }
                return;
            }

            try {
                // Step 1: Send file metadata
                JSONObject fileInfo = new JSONObject();
                fileInfo.put("type", "file_info");
                fileInfo.put("filename", file.getName());
                fileInfo.put("size", file.length());
                out.println(fileInfo.toString());
                out.flush();
                log("[FILE] Metadata sent for: " + fileName);

                // Step 2: Wait for file_ready response
                String response = in.readLine();
                JSONObject readyMsg = new JSONObject(response);
                if (!"file_ready".equalsIgnoreCase(readyMsg.optString("type"))) {
                    log("[FILE ERROR] Client not ready");
                    return;
                }

                // Step 3: Send the actual file
                try (
                    BufferedOutputStream bos = new BufferedOutputStream(client.getOutputStream());
                    FileInputStream fis = new FileInputStream(file)
                ) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = fis.read(buffer)) > 0) {
                        bos.write(buffer, 0, count);
                    }
                    bos.flush();
                    log("[FILE] File sent: " + fileName);
                }

                // Step 4: Notify client of transfer completion
                JSONObject done = new JSONObject();
                done.put("type", "transfer_complete");
                done.put("filename", file.getName());
                out.println(done.toString());
                out.flush();

            } catch (Exception e) {
                log("[FILE ERROR] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerGUI().setVisible(true));
    }
}