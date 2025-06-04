# server-client-system-in-java
A Java-based client-server chat app with a Swing GUI supporting real-time messaging, file transfer, and command execution using JSON over sockets. It features multithreaded server handling and an interactive client interface for smooth communication and file exchange.

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


---

## 🚀 Getting Started

### 1️⃣ Prerequisites

- ✅ Java JDK 8 or later
- ✅ Any IDE (e.g., IntelliJ, Eclipse, VS Code) or terminal
- ✅ [`org.json`](https://mvnrepository.com/artifact/org.json/json) library (download `json.jar`)

### 2️⃣ Compilation

In terminal:

```bash
javac -cp .;json.jar Server.java ClientHandler.java ClientAppGUI.java

