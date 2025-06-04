# server-client-system-in-java
A Java-based client-server chat app with a Swing GUI supporting real-time messaging, file transfer, and command execution using JSON over sockets. It features multithreaded server handling and an interactive client interface for smooth communication and file exchange.

# 💬 Java Chat Application with GUI, File Transfer, and Command Execution

This is a multithreaded Java Client-Server Chat Application using **Swing GUI** on the client side and a **JSON-based protocol** for communication. It supports:

- ✅ Text messaging between users
- 📁 File requests and downloads
- 🛠️ Command execution (like `mkdir`, `ls`, etc.)
- 🚫 User management (connect/disconnect)
- 💡 Real-time feedback via GUI

---

## 📦 Features

| Feature                | Description                                              |
|------------------------|----------------------------------------------------------|
| 🧑‍💻 GUI Client           | Built using Java Swing for easy message/file interaction |
| 🌐 Multithreaded Server | Handles multiple clients simultaneously                 |
| 📩 JSON Messaging       | Uses `org.json` for structured communication            |
| 📁 File Transfer        | Clients can request and receive files from the server   |
| ⚙️ Command Execution     | Simple commands like `mkdir`, `ls`, etc. are supported |
| 🔌 Connect/Disconnect   | Real-time status and clean disconnection handling       |

---

## 🗂️ Project Structure

JavaChatApp/
├── ClientAppGUI.java # GUI client code
├── Server.java # Main server code
├── ClientHandler.java # Handles individual clients on server
├── downloads/ # Where files are saved on client side
├── shared_files/ # Server-side files available for clients
├── json.jar # JSON library (org.json)
└── README.md # You're here!

