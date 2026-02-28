import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 5000;
    private static Map<String, ClientHandler> clients = new HashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("🚀 Chat Server started on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("✅ New client connected: " + socket);

                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static synchronized void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        System.out.println(username + " joined the chat.");
    }

    static synchronized void removeClient(String username) {
        clients.remove(username);
        System.out.println(username + " disconnected.");
    }

    static synchronized void sendMessage(String sender, String receiver, String message) {
        ClientHandler client = clients.get(receiver);
        if (client != null) {
            client.send(sender + ": " + message);
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void send(String message) {
        out.println(message);
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            username = in.readLine(); // first message is username
            ChatServer.registerClient(username, this);

            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String receiver = parts[0];
                    String message = parts[1];
                    ChatServer.sendMessage(username, receiver, message);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            ChatServer.removeClient(username);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
