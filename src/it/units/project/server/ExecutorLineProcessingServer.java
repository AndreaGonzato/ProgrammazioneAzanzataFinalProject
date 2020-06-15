package it.units.project.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class ExecutorLineProcessingServer {
  private final int port;
  private final String quitCommand;
  private final Function<String, String> commandProcessingFunction;
  private final ExecutorService processComputationRequests;
  private final ExecutorService connectionHandler;

  public ExecutorLineProcessingServer(int port, String quitCommand, Function<String, String> commandProcessingFunction, int concurrentClients) {
    this.port = port;
    this.quitCommand = quitCommand;
    this.commandProcessingFunction = commandProcessingFunction;
    processComputationRequests = Executors.newFixedThreadPool(concurrentClients);
    connectionHandler = Executors.newCachedThreadPool();
  }

  public void start() throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      while (true) {
        try {
          final Socket socket = serverSocket.accept();
          connectionHandler.submit(() -> {
            try (socket) {
              BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
              BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
              while (true) {
                String command = br.readLine();
                if (command == null) {
                  System.err.println("Client abruptly closed connection");
                  break;
                }
                if (command.equals(quitCommand)) {
                  break;
                }
                if (command.length() >= 5 && command.substring(0, 5).equals("STAT_")) {
                  // stat request
                  bw.write(commandProcessingFunction.apply(command) + System.lineSeparator());
                  bw.flush();
                } else {
                  // computation request
                  processComputationRequests.submit(() -> {
                    try {
                      bw.write(commandProcessingFunction.apply(command) + System.lineSeparator());
                      bw.flush();
                    } catch (IOException e) {
                      System.err.printf("IO error: %s", e);
                    }
                  });
                }

              }
            } catch (IOException e) {
              System.err.printf("IO error: %s", e);
            }
          });
        } catch (IOException e) {
          System.err.printf("Cannot accept connection due to %s", e);
        }
      }
    } finally {
      processComputationRequests.shutdown();
    }
  }


}