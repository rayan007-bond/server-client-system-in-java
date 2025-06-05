import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import org.json.JSONObject;

public class ClientAppGUI extends JFrame {
    private JTextArea chatArea;
    private JTextField messageField, usernameField;
    private JButton sendButton, connectButton, disconnectButton, fileButton;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;
    private boolean connected = false;
    private File currentDirectory = new File(System.getProperty("user.dir"));

    public ClientAppGUI() {
        setTitle("Client Chat GUI");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        messageField = new JTextField(30);
        sendButton = new JButton("Send");
        sendButton.setEnabled(false);

        usernameField = new JTextField(15);
        connectButton = new JButton("Connect");
        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        fileButton = new JButton("Request File");
        fileButton.setEnabled(false);

        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Username:"));
        topPanel.add(usernameField);
        topPanel.add(connectButton);
        topPanel.add(disconnectButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);

        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        connectButton.addActionListener(e -> connect());
        disconnectButton.addActionListener(e -> disconnect());
        sendButton.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> requestFile());
        messageField.addActionListener(e -> sendMessage());
    }

    private void connect() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a username first.");
            return;
        }

        try {
            socket = new Socket("localhost", 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send login message
            JSONObject loginJson = new JSONObject();
            loginJson.put("type", "login");
            loginJson.put("username", username);
            out.println(loginJson.toString());

            connected = true;
            sendButton.setEnabled(true);
            fileButton.setEnabled(true);
            connectButton.setEnabled(false);
            disconnectButton.setEnabled(true);
            usernameField.setEditable(false);
            chatArea.append("[SYSTEM] Connected to server.\n");

            listenerThread = new Thread(this::listenForMessages);
            listenerThread.start();

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + ex.getMessage());
        }
    }

    private void listenForMessages() {
        String line;
        try {
            while ((line = in.readLine()) != null) {
                JSONObject msg = new JSONObject(line);
                String type = msg.optString("type", "");

                switch (type) {
                    case "welcome":
                        chatArea.append("[SERVER] " + msg.optString("message") + "\n");
                        break;
                    case "message":
                        String from = msg.optString("from", "unknown");
                        String message = msg.optString("message", "");
                        chatArea.append("[" + from + "] " + message + "\n");
                        break;
                    case "notification":
                        chatArea.append("[NOTIFICATION] " + msg.optString("message") + "\n");
                        break;
                    case "commandResult":
                        chatArea.append("[COMMAND RESULT] " + msg.optString("result") + "\n");
                        break;
                    case "shutdown":
                        chatArea.append("[SERVER] " + msg.optString("message") + "\n");
                        disconnect();
                        break;
                    case "disconnect":
                        chatArea.append("[SERVER] You have been disconnected: " + msg.optString("message") + "\n");
                        disconnect();
                        break;
                    case "file_info":
                        handleFileTransfer(msg);
                        break;
                    case "file_error":
                        chatArea.append("[FILE ERROR] " + msg.optString("message") + "\n");
                        break;
                    case "transfer_complete":
                        chatArea.append("[FILE TRANSFER] " + msg.optString("filename") + " received successfully\n");
                        break;
                    default:
                        chatArea.append("[UNKNOWN MESSAGE] " + line + "\n");
                        break;
                }
            }
        } catch (IOException e) {
            if (connected) {
                chatArea.append("[ERROR] Connection lost: " + e.getMessage() + "\n");
                disconnect();
            }
        }
    }

    private void sendMessage() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "You are not connected to the server.");
            return;
        }

        String text = messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        try {
            JSONObject json = new JSONObject();

            // If the message starts with a known command like mkdir, ls, etc.
            if (text.matches("^(mkdir|ls|rmdir|touch)\\b.*")) {
                json.put("type", "command");
                json.put("command", text);
                json.put("username", usernameField.getText().trim());
            } else {
                // Normal chat message to all
                json.put("type", "message");
                json.put("to", "all");  // Broadcast to all
                json.put("message", text);
                json.put("username", usernameField.getText().trim());
            }

            out.println(json.toString());
            messageField.setText("");
        } catch (Exception e) {
            chatArea.append("[ERROR] Failed to send message: " + e.getMessage() + "\n");
        }
    }

    private void requestFile() {
        if (!connected) {
            JOptionPane.showMessageDialog(this, "You are not connected to the server.");
            return;
        }

        JFileChooser chooser = new JFileChooser(currentDirectory);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            JSONObject json = new JSONObject();
            json.put("type", "file_request");
            json.put("username", usernameField.getText().trim());
            json.put("filename", selected.getName());
            out.println(json.toString());
            currentDirectory = chooser.getCurrentDirectory();
        }
    }

    private void handleFileTransfer(JSONObject fileInfo) {
        String fileName = fileInfo.optString("filename");
        long fileSize = fileInfo.optLong("size");

        SwingUtilities.invokeLater(() -> {
            chatArea.append("[FILE TRANSFER] Receiving file: " + fileName + " (" + fileSize + " bytes)\n");
        });

        // Create downloads folder if not exists
        File downloadDir = new File("downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdir();
        }

        File file = new File(downloadDir, fileName);

        try {
            // Send ready confirmation
            JSONObject readyMsg = new JSONObject();
            readyMsg.put("type", "file_ready");
            out.println(readyMsg.toString());
            out.flush();

            // Receive file data
            try (FileOutputStream fos = new FileOutputStream(file);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 InputStream is = socket.getInputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                while (totalRead < fileSize &&
                       (bytesRead = is.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRead))) != -1) {
                    bos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    final long currentTotal = totalRead;
                    SwingUtilities.invokeLater(() -> {
                        chatArea.append("[FILE TRANSFER] Progress: " + currentTotal + "/" + fileSize + " bytes\n");
                    });
                }
                bos.flush();
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("[FILE TRANSFER] File saved to: " + file.getAbsolutePath() + "\n");
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                chatArea.append("[FILE ERROR] " + e.getMessage() + "\n");
            });
        }
    }

    private void disconnect() {
        if (!connected) return;

        try {
            // Send disconnect message
            JSONObject disconnectMsg = new JSONObject();
            disconnectMsg.put("type", "disconnect");
            disconnectMsg.put("username", usernameField.getText().trim());
            out.println(disconnectMsg.toString());
            
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            chatArea.append("[ERROR] Error closing socket: " + e.getMessage() + "\n");
        }

        connected = false;
        sendButton.setEnabled(false);
        fileButton.setEnabled(false);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        usernameField.setEditable(true);
        chatArea.append("[SYSTEM] Disconnected from server.\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientAppGUI().setVisible(true));
    }
} 