import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.util.Duration;
import java.sql.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends Application {

    // IMPORTANT: Ensure you have the MySQL Connector/J JAR in your project's classpath
    // to resolve "No suitable driver found" error.

    class SocketClient {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;
        private volatile boolean running = true;

        public SocketClient(String serverIP, int port, String username) throws IOException {
            this.username = username;
            socket = new Socket(serverIP, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send username to server
            out.println(username);

            // Start listener thread for incoming messages
            new Thread(() -> {
                try {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        String[] parts = line.split(":", 2);
                        if (parts.length == 2) {
                            String sender = parts[0];
                            String message = parts[1];

                            // Save to database
                            long timestamp = System.currentTimeMillis();
                            saveMessageToDatabase(sender, username, message, timestamp);

                            // Update JavaFX UI
                            Platform.runLater(() -> {
                                String selected = contactsList.getSelectionModel().getSelectedItem();
                                // Only show if the sender is the currently selected contact
                                if (selected != null && selected.split(" ")[0].equals(sender)) {
                                    // Add timestamp to prevent re-display by poller (optional but safer)
                                    displayedTimestamps.add(timestamp);
                                    HBox bubble = createReceivedBubble(sender + ": " + message);
                                    chatArea.getChildren().add(bubble);
                                    fadeIn(bubble);
                                    scrollChatToBottom();
                                }
                                // Force update contact list to show new message/notification if you implement it
                                updateContactList();
                            });
                        }
                    }
                } catch (SocketException se) {
                    if (running) {
                        System.err.println("Socket connection closed unexpectedly: " + se.getMessage());
                    }
                } catch (Exception e) {
                    if (running) {
                        e.printStackTrace();
                    }
                } finally {
                    // Try to reconnect or show a server error message to the user
                    Platform.runLater(() -> System.out.println("Disconnected from server."));
                }
            }, "SocketListener-" + username).start();
        }

        public void sendMessage(String receiver, String message) {
            if (out != null && !socket.isClosed()) {
                // Format: receiver:message
                out.println(receiver + ":" + message);
            } else {
                Platform.runLater(() -> showAlert("Connection Error", "Not connected to the chat server."));
            }
        }

        public void close() {
            running = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private SocketClient socketClient;
    private Stage stage;
    private VBox chatArea;
    private TextField messageField;
    private ListView<String> contactsList;
    private boolean darkMode = false;
    private ScrollPane chatScroll;
    private ScheduledExecutorService scheduler;

    // IMPORTANT: DATABASE URL MODIFIED TO USE SERVER IP
    private final String URL = "jdbc:mysql://127.0.0.1:3306/chat_app?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    private final String USER = "root";
    private final String PASSWORD = "Abdullah@Hussain"; // Make sure this is correct

    // IMPORTANT: Replace with your actual server IP
    private final String SERVER_IP = "127.0.0.1"; // Change this to your server IP
    private final int SERVER_PORT = 5000;

    private MessageService messageService = new MessageService();
    private String currentUser;
    private Set<Long> displayedTimestamps = Collections.synchronizedSet(new HashSet<>());
    private String currentContact = null;

    @Override
    public void start(Stage primaryStage) {
        // --- CRITICAL DRIVER LOAD FIX ---
        try {
            // Explicitly load the MySQL JDBC driver (Less necessary in modern Java, but good for stability)
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Please ensure mysql-connector-j.jar is in your classpath.");
            // Optionally, show an alert to the user and stop the app
            // Platform.runLater(() -> showAlert("Configuration Error", "Database driver not found. Check console for details."));
            // return;
        }
        // --- END CRITICAL FIX ---

        this.stage = primaryStage;
        stage.setTitle("MyChatApp");
        stage.setOnCloseRequest(e -> {
            cleanup();
            // Shut down the scheduler cleanly
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        });
        showLoginScene();
    }

    private void cleanup() {
        if (socketClient != null) {
            socketClient.close();
        }
        if (currentUser != null) {
            messageService.userOffline(currentUser);
        }
    }

    // ---------------- LOGIN / SIGNUP SCENE ----------------
    private void showLoginScene() {
        Label title = new Label("Welcome to MyChatApp");
        title.setFont(Font.font(24));
        title.setStyle("-fx-font-weight: bold;");

        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter Username");
        usernameField.setPrefWidth(250);
        usernameField.setStyle("-fx-font-size: 14px;");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter Password");
        passwordField.setPrefWidth(250);
        passwordField.setStyle("-fx-font-size: 14px;");

        Button loginBtn = new Button("Login");
        loginBtn.setPrefWidth(120);
        loginBtn.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;");

        Button signupBtn = new Button("Sign Up");
        signupBtn.setPrefWidth(120);
        signupBtn.setStyle("-fx-background-color: #28A745; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;");

        Label message = new Label();
        message.setWrapText(true);

        HBox buttonBox = new HBox(15, loginBtn, signupBtn);
        buttonBox.setAlignment(Pos.CENTER);

        VBox box = new VBox(15, title, usernameField, passwordField, buttonBox, message);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        box.setStyle("-fx-background-color: #F8F9FA;");

        // Hover effects
        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle("-fx-background-color: #0056CC; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;"));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;"));

        signupBtn.setOnMouseEntered(e -> signupBtn.setStyle("-fx-background-color: #218838; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;"));
        signupBtn.setOnMouseExited(e -> signupBtn.setStyle("-fx-background-color: #28A745; -fx-text-fill: white; -fx-font-size: 14px; -fx-cursor: hand;"));

        signupBtn.setOnAction(e -> {
            String user = usernameField.getText().trim();
            String pass = passwordField.getText().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                message.setStyle("-fx-text-fill: red;");
                message.setText("Username and password cannot be empty!");
                return;
            }

            if (signup(user, pass)) {
                message.setStyle("-fx-text-fill: green;");
                message.setText("Sign Up Successful! Now Login.");
                passwordField.clear();
            } else {
                message.setStyle("-fx-text-fill: red;");
                message.setText("Signup failed (username may already exist or DB error).");
            }
        });

        loginBtn.setOnAction(e -> performLogin(usernameField, passwordField, message));
        passwordField.setOnAction(e -> performLogin(usernameField, passwordField, message));

        Scene scene = new Scene(box, 400, 350);
        stage.setScene(scene);
        stage.show();
    }

    private void performLogin(TextField usernameField, PasswordField passwordField, Label message) {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            message.setStyle("-fx-text-fill: red;");
            message.setText("Username and password cannot be empty!");
            return;
        }

        if (login(user, pass)) {
            currentUser = user;
            loadAllUsers();
            messageService.userOnline(currentUser); // Update user service status
            showChatInterface(user);

            // Connect to server
            try {
                socketClient = new SocketClient(SERVER_IP, SERVER_PORT, currentUser);
            } catch (IOException ex) {
                System.err.println("Could not connect to server: " + ex.getMessage());
                // Use a separate message box or alert for connection status
                Platform.runLater(() -> showAlert("Server Connection", "Could not connect to the chat server at " + SERVER_IP + ":" + SERVER_PORT + ". Messages will be sent via database polling only."));
            }
        } else {
            message.setStyle("-fx-text-fill: red;");
            message.setText("Login failed! Check your credentials.");
        }
    }

    // ---------------- CHAT INTERFACE ----------------
    private void showChatInterface(String username) {
        BorderPane root = new BorderPane();
        root.setPrefSize(850, 600);

        // Stop any previous background tasks before starting new ones
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // LEFT PANEL
        Label contactsLabel = new Label("Contacts");
        contactsLabel.setFont(Font.font(18));
        contactsLabel.setStyle("-fx-font-weight: bold;");
        contactsLabel.setPadding(new Insets(15, 10, 10, 15));

        contactsList = new ListView<>();
        contactsList.setPrefWidth(220);
        contactsList.setStyle("-fx-font-size: 14px;");

        // NEW: Remove Contact Button
        Button removeContactBtn = createStyledButton("➖ Remove Contact", "#DC3545");
        removeContactBtn.setPrefWidth(220);

        VBox leftPanel = new VBox(contactsLabel, contactsList, removeContactBtn); // Button added here
        leftPanel.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;");
        root.setLeft(leftPanel);

        // Clicking a contact loads chat with animation
        contactsList.setOnMouseClicked(e -> {
            String selected = contactsList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                currentContact = selected.split(" ")[0];
                loadChatHistory(currentUser, currentContact);
            }
        });

        // TOP BAR
        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(12));
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 1 0;");

        Button addContactBtn = createStyledButton("➕ Add Contact", "#28A745");
        Button personalizeBtn = createStyledButton("🌓 Theme", "#6C757D");
        Button logoutBtn = createStyledButton("Logout", "#DC3545");

        topBar.getChildren().addAll(addContactBtn, personalizeBtn, logoutBtn);
        root.setTop(topBar);

        // CHAT AREA
        chatArea = new VBox(12);
        chatArea.setPadding(new Insets(15));
        chatScroll = new ScrollPane(chatArea);
        chatScroll.setFitToWidth(true);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScroll.setStyle("-fx-background-color: white; -fx-background: white;");
        root.setCenter(chatScroll);

        // BOTTOM BAR
        HBox bottomBar = new HBox(12);
        bottomBar.setPadding(new Insets(12));
        bottomBar.setAlignment(Pos.CENTER);
        bottomBar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 1 0 0 0;");

        messageField = new TextField();
        messageField.setPromptText("Type a message...");
        messageField.setPrefWidth(650);
        messageField.setStyle("-fx-font-size: 14px; -fx-padding: 10;");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendBtn = new Button("Send");
        sendBtn.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 20; -fx-cursor: hand;");
        sendBtn.setOnMouseEntered(e -> sendBtn.setStyle("-fx-background-color: #0056CC; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 20; -fx-cursor: hand;"));
        sendBtn.setOnMouseExited(e -> sendBtn.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 10 20; -fx-cursor: hand;"));

        sendBtn.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        bottomBar.getChildren().addAll(messageField, sendBtn);
        root.setBottom(bottomBar);

        // BUTTON ACTIONS
        addContactBtn.setOnAction(e -> addContact());
        personalizeBtn.setOnAction(e -> toggleTheme(root, leftPanel, topBar, bottomBar));
        applyLightMode(root, leftPanel, topBar, bottomBar);
        // NEW: Remove Contact Action
        removeContactBtn.setOnAction(e -> removeContact());

        logoutBtn.setOnAction(e -> {
            cleanup();
            currentUser = null;
            currentContact = null;
            displayedTimestamps.clear();
            // Shut down the scheduler before changing scenes
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            showLoginScene();
        });

        // SHOW SCENE
        Scene scene = new Scene(root);
        stage.setScene(scene);

        // START BACKGROUND TASKS
        updateContactList();
        startContactRefresh();
        startMessagePolling(username);
    }

    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 16; -fx-cursor: hand;");

        // Hover effect
        btn.setOnMouseEntered(e -> {
            btn.setStyle("-fx-background-color: derive(" + color + ", -20%); -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 16; -fx-cursor: hand;");
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 8 16; -fx-cursor: hand;");
        });

        return btn;
    }

    private void loadAllUsers() {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT username FROM users");
            while (rs.next()) {
                String name = rs.getString("username");
                messageService.addUser(name);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Database Error", "Failed to load all users.");
        }
    }

    // ---------------- ADD CONTACT ----------------
    private void addContact() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Contact");
        dialog.setHeaderText("Add a new contact to your list");
        dialog.setContentText("Enter contact username:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            name = name.trim();
            if (name.isEmpty()) {
                showAlert("Error", "Contact name cannot be empty!");
                return;
            }

            if (name.equals(currentUser)) {
                showAlert("Error", "You cannot add yourself as a contact!");
                return;
            }

            if (messageService.findUser(name) != null) {
                showAlert("Info", "Contact is already in your list.");
                return;
            }

            // Check if user exists in database
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                String query = "SELECT username FROM users WHERE username = ?";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, name);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    messageService.addUser(name);
                    updateContactList();
                    showAlert("Success", "Contact added successfully!");
                } else {
                    showAlert("Error", "User not found in the system!");
                }
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to add contact! (DB Error)");
            }
        });
    }

    // ---------------- REMOVE CONTACT ----------------
    private void removeContact() {
        String selectedItem = contactsList.getSelectionModel().getSelectedItem();

        if (selectedItem == null) {
            showAlert("Error", "Please select a contact to remove first!");
            return;
        }

        String contactName = selectedItem.split(" ")[0];

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Removal");
        confirmation.setHeaderText("Remove " + contactName + "?");
        confirmation.setContentText("Are you sure you want to remove this contact?");

        Optional<ButtonType> result = confirmation.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            // Remove from the local in-memory list
            messageService.removeUser(contactName);

            // Remove from selection and update UI
            currentContact = null;
            chatArea.getChildren().clear();
            updateContactList();

            showAlert("Success", contactName + " has been removed from your list.");
        }
    }


    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void updateContactList() {
        Platform.runLater(() -> {
            String currentSelection = null;
            if (contactsList.getSelectionModel().getSelectedItem() != null) {
                currentSelection = contactsList.getSelectionModel().getSelectedItem().split(" ")[0];
            }

            contactsList.getItems().clear();
            // Sort users by name before displaying
            ArrayList<UserData> sortedUsers = new ArrayList<>(messageService.allUsers);
            sortedUsers.sort(Comparator.comparing(u -> u.name));

            for (UserData u : sortedUsers) {
                if (u.name.equals(currentUser)) continue;
                String status = messageService.isOnline(u.name) ? "🟢" : "⚫";
                contactsList.getItems().add(u.name + " " + status);
            }

            // Restore selection
            if (currentSelection != null) {
                final String selection = currentSelection;
                for (String item : contactsList.getItems()) {
                    if (item.startsWith(selection + " ")) {
                        contactsList.getSelectionModel().select(item);
                        break;
                    }
                }
                // If the current contact is no longer in the list (e.g., deleted, though not implemented)
                if (contactsList.getSelectionModel().getSelectedItem() == null) {
                    currentContact = null;
                }
            }
        });
    }

    // ---------------- TOGGLE DARK/LIGHT MODE ----------------
    private void toggleTheme(BorderPane root, VBox leftPanel, HBox topBar, HBox bottomBar) {
        if (darkMode) {
            applyLightMode(root, leftPanel, topBar, bottomBar);
            darkMode = false;
        } else {
            applyDarkMode(root, leftPanel, topBar, bottomBar);
            darkMode = true;
        }
        // Re-apply bubble styles to reflect new theme (This is an incomplete fix for all bubbles)
        // A better solution involves using CSS stylesheets.
        for (javafx.scene.Node node : chatArea.getChildren()) {
            if (node instanceof HBox) {
                HBox bubbleBox = (HBox) node;
                if (!bubbleBox.getChildren().isEmpty() && bubbleBox.getChildren().get(0) instanceof Label) {
                    Label label = (Label) bubbleBox.getChildren().get(0);
                    // Very simple guess based on existing styles
                    String style = label.getStyle();
                    if (style.contains("#0078FF")) { // Sent bubble
                        // Sent bubble style is fine, generally
                    } else if (style.contains("#E8E8E8")) { // Received bubble (Light Mode)
                        if (darkMode) {
                            label.setStyle("-fx-background-color: #4A4A4A; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 15; -fx-font-size: 13px;");
                        }
                    } else if (style.contains("#4A4A4A")) { // Received bubble (Dark Mode)
                        if (!darkMode) {
                            label.setStyle("-fx-background-color: #E8E8E8; -fx-text-fill: black; -fx-padding: 10 14; -fx-background-radius: 15; -fx-font-size: 13px;");
                        }
                    }
                }
            }
        }
    }

    private void applyLightMode(BorderPane root, VBox leftPanel, HBox topBar, HBox bottomBar) {
        root.setStyle("-fx-background-color: white;");
        leftPanel.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;");
        topBar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 0 0 1 0;");
        bottomBar.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E0E0E0; -fx-border-width: 1 0 0 0;");
        chatArea.setStyle("-fx-background-color: white;");
        chatScroll.setStyle("-fx-background-color: white; -fx-background: white;");
    }

    private void applyDarkMode(BorderPane root, VBox leftPanel, HBox topBar, HBox bottomBar) {
        root.setStyle("-fx-background-color: #1E1E1E;");
        leftPanel.setStyle("-fx-background-color: #2D2D2D; -fx-border-color: #3A3A3A; -fx-border-width: 0 1 0 0;");
        topBar.setStyle("-fx-background-color: #2D2D2D; -fx-border-color: #3A3A3A; -fx-border-width: 0 0 1 0;");
        bottomBar.setStyle("-fx-background-color: #2D2D2D; -fx-border-color: #3A3A3A; -fx-border-width: 1 0 0 0;");
        chatArea.setStyle("-fx-background-color: #1E1E1E;");
        chatScroll.setStyle("-fx-background-color: #1E1E1E; -fx-background: #1E1E1E;");
    }

    // ---------------- DATABASE METHODS ----------------
    private boolean signup(String username, String password) {
        if (username.isEmpty() || password.isEmpty()) return false;
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String query = "INSERT INTO users (username, password) VALUES (?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            System.err.println("Signup failed: Username already exists.");
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            // Inform user about DB connection issues
            if (e.getSQLState().startsWith("08")) { // SQLState for connection errors
                Platform.runLater(() -> showAlert("Database Error", "Failed to connect to the database. Check console for details."));
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean login(String username, String password) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String query = "SELECT * FROM users WHERE username=? AND password=?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            // Inform user about DB connection issues
            if (e.getSQLState().startsWith("08")) { // SQLState for connection errors
                Platform.runLater(() -> showAlert("Database Error", "Failed to connect to the database. Check console for details."));
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------- CHAT MESSAGES ----------------
    private void sendMessage() {
        String text = messageField.getText().trim();
        String selected = contactsList.getSelectionModel().getSelectedItem();

        if (text.isEmpty()) return;

        if (selected == null) {
            showAlert("Error", "Please select a contact first!");
            return;
        }

        String receiver = selected.split(" ")[0];

        try {
            long timestamp = System.currentTimeMillis();

            // 1. Save to database immediately
            saveMessageToDatabase(currentUser, receiver, text, timestamp);
            displayedTimestamps.add(timestamp);

            // 2. Send via SocketClient if connected (real-time)
            if (socketClient != null) {
                socketClient.sendMessage(receiver, text);
            }

            // 3. Show in chat area with animation
            HBox bubble = createSentBubble("You: " + text);
            chatArea.getChildren().add(bubble);
            fadeIn(bubble);
            messageField.clear();

            // 4. Scroll to bottom
            scrollChatToBottom();

        } catch (Exception e) {
            e.printStackTrace();
            chatArea.getChildren().add(createReceivedBubble("⚠ Message failed to send."));
        }
    }

    private void fadeIn(HBox node) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(300), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }

    private void startContactRefresh() {
        // Use ScheduledExecutorService for cleaner background tasks
        scheduler.scheduleAtFixedRate(this::updateContactList, 5, 5, TimeUnit.SECONDS);
    }

    private HBox createSentBubble(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(450);
        label.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 15; -fx-font-size: 13px;");
        HBox box = new HBox(label);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(2, 0, 2, 50));
        return box;
    }

    private HBox createReceivedBubble(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(450);
        String bgColor = darkMode ? "#4A4A4A" : "#E8E8E8";
        String textColor = darkMode ? "white" : "black";
        label.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + "; -fx-padding: 10 14; -fx-background-radius: 15; -fx-font-size: 13px;");
        HBox box = new HBox(label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(2, 50, 2, 0));
        return box;
    }
    private String lastLoadedContact = null;

    private void loadChatHistory(String user, String contact) {
        // Skip reload if clicking same contact
        if (contact.equals(lastLoadedContact)) {
            scrollChatToBottom(); // Just scroll, don't reload
            return;
        }

        lastLoadedContact = contact;
        chatArea.getChildren().clear();
        displayedTimestamps.clear();

        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String query = "SELECT sender, text, timestamp FROM messages " +
                    "WHERE (sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?) " +
                    "ORDER BY timestamp ASC";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, user);
            stmt.setString(2, contact);
            stmt.setString(3, contact);
            stmt.setString(4, user);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String sender = rs.getString("sender");
                String text = rs.getString("text");
                long timestamp = rs.getLong("timestamp");
                displayedTimestamps.add(timestamp);

                HBox bubble;
                if (sender.equals(user)) {
                    bubble = createSentBubble("You: " + text);
                } else {
                    bubble = createReceivedBubble(sender + ": " + text);
                }
                chatArea.getChildren().add(bubble);
            }

            Platform.runLater(this::scrollChatToBottom);

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Database Error", "Failed to load chat history.");
        }
    }

    private void saveMessageToDatabase(String sender, String receiver, String text, long timestamp) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String query = "INSERT INTO messages (sender, receiver, text, timestamp) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, sender);
            stmt.setString(2, receiver);
            stmt.setString(3, text);
            stmt.setLong(4, timestamp);
            stmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to save message to database.");
        }
    }

    private void startMessagePolling(String username) {
        scheduler.scheduleAtFixedRate(() -> {
            // Skip polling if socket is connected and working
            if (socketClient != null) {
                return; // Socket is handling messages, no need to poll
            }

            // Only poll if socket is disconnected
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                String query = "SELECT sender, text, timestamp FROM messages WHERE receiver = ? ORDER BY timestamp ASC";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    long timestamp = rs.getLong("timestamp");
                    String sender = rs.getString("sender");
                    String msg = rs.getString("text");

                    if (!displayedTimestamps.contains(timestamp)) {
                        displayedTimestamps.add(timestamp);

                        Platform.runLater(() -> {
                            if (currentContact != null && currentContact.equals(sender)) {
                                HBox bubble = createReceivedBubble(sender + ": " + msg);
                                chatArea.getChildren().add(bubble);
                                fadeIn(bubble);
                                scrollChatToBottom();
                            }
                            updateContactList();
                        });
                    }
                }
            } catch (SQLException e) {
                if (!e.getSQLState().startsWith("08")) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 2, 2, TimeUnit.SECONDS);
    }

    private void scrollChatToBottom() {
        // Method 1: Double Platform.runLater
        Platform.runLater(() -> {
            chatArea.applyCss();
            chatArea.layout();

            Platform.runLater(() -> {
                chatScroll.setVvalue(1.0);
            });
        });
    }

    public static void main(String[] args) {
        Application.launch(args);
    }

    // ===================== MESSAGE SYSTEM CLASSES (Retained for completeness) =====================

    class Message {
        String sender;
        String receiver;
        String text;

        Message(String s, String r, String t) {
            sender = s;
            receiver = r;
            text = t;
        }
    }

    class ChatNode {
        Message msg;
        ChatNode next;

        ChatNode(Message m) {
            msg = m;
            next = null;
        }
    }

    class ChatHistory {
        ChatNode head;

        void add(Message m) {
            ChatNode n = new ChatNode(m);
            if (head == null) {
                head = n;
                return;
            }
            ChatNode temp = head;
            while (temp.next != null) {
                temp = temp.next;
            }
            temp.next = n;
        }
    }

    class MessageQueue {
        Message[] arr;
        int size;
        int front;
        int rear;

        MessageQueue(int s) {
            size = s;
            arr = new Message[size];
            front = -1;
            rear = -1;
        }

        void enqueue(Message m) {
            if ((rear + 1) % size == front) return;
            if (front == -1) {
                front = 0;
                rear = 0;
                arr[rear] = m;
                return;
            }
            rear = (rear + 1) % size;
            arr[rear] = m;
        }

        Message dequeue() {
            if (front == -1) return null;
            Message m = arr[front];
            if (front == rear) {
                front = -1;
                rear = -1;
                return m;
            }
            front = (front + 1) % size;
            return m;
        }

        boolean isEmpty() {
            return front == -1;
        }
    }

    class UserData {
        String name;
        boolean online;
        MessageQueue pending;
        ChatHistory history;

        UserData(String n, int queueSize) {
            name = n;
            online = false;
            pending = new MessageQueue(queueSize);
            history = new ChatHistory();
        }
    }

    class MessageService {
        ArrayList<UserData> allUsers;
        Set<String> onlineUsers; // Changed to Set for efficient lookup

        MessageService() {
            allUsers = new ArrayList<>();
            onlineUsers = Collections.synchronizedSet(new HashSet<>());
        }

        void addUser(String name) {
            if (findUser(name) == null)
                allUsers.add(new UserData(name, 50));
        }

        UserData findUser(String name) {
            for (UserData u : allUsers) {
                if (u.name.equals(name)) return u;
            }
            return null;
        }

        // NEW: Remove user from the in-memory list
        void removeUser(String name) {
            UserData userToRemove = null;
            for (UserData u : allUsers) {
                if (u.name.equals(name)) {
                    userToRemove = u;
                    break;
                }
            }
            if (userToRemove != null) {
                allUsers.remove(userToRemove);
                onlineUsers.remove(name); // Ensure they are removed from online set too
            }
        }

        boolean isOnline(String name) {
            return onlineUsers.contains(name);
        }

        // Simplified userOnline method as UI updates are handled by Poller/SocketClient
        void userOnline(String name) {
            UserData u = findUser(name);
            if (u == null) return;

            u.online = true;
            onlineUsers.add(name);
        }

        void userOffline(String name) {
            UserData u = findUser(name);
            if (u != null) {
                u.online = false;
            }
            onlineUsers.remove(name);
        }

        void sendMessage(String senderName, String receiverName, String text) {
            UserData sender = findUser(senderName);
            UserData receiver = findUser(receiverName);
            if (sender == null || receiver == null) {
                System.out.println("Sender or receiver not found.");
                return;
            }

            Message m = new Message(senderName, receiverName, text);

            if (isOnline(receiverName)) {
                deliverToHistories(sender, receiver, m);
            } else {
                receiver.pending.enqueue(m);
            }
        }

        void deliverToHistories(UserData a, UserData b, Message m) {
            a.history.add(m);
            b.history.add(m);
        }
    }
}