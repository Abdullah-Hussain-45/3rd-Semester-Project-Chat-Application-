# JavaFX Real-Time Chat Application 💬

## About The Project
I have developed this robust, real-time client-server chat application to demonstrate my skills in Java, network programming, and database management. The application provides a seamless messaging experience where multiple users can connect, chat instantly, and manage their contacts. 

By combining Java Sockets for real-time communication, a JavaFX frontend for a modern user experience, and a MySQL backend for data persistence, this project serves as a comprehensive full-stack desktop application.

## Key Features
* **User Authentication:** Secure Login and Sign-Up functionality verified against a MySQL database.
* **Real-Time Communication:** Instant, bidirectional message delivery using TCP/IP Sockets and Multithreading (`ClientHandler`).
* **Modern GUI with Theming:** An intuitive JavaFX interface featuring dynamic Light and Dark mode toggles.
* **Chat History Persistence:** All conversations are stored in the database and retrieved instantly when opening a chat.
* **Resilient Architecture:** Features a background database polling mechanism (`ScheduledExecutorService`) as a fallback to ensure messages are received even if socket connection drops.
* **Contact Management:** Users can easily add new users to their contact list or remove existing ones.
* **Custom Data Structures:** Implements core Computer Science concepts like Circular Queues (`MessageQueue`) and Linked Lists (`ChatHistory`) for optimal in-memory message processing.

## Built With
* **Language:** Java
* **UI Framework:** JavaFX
* **Networking:** Java Sockets (TCP/IP) & ServerSockets
* **Database:** MySQL
* **Connectivity:** JDBC (mysql-connector-j)
* **Concurrency:** Java Multithreading & Executors
