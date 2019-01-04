package sample;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Controller {

    @FXML
    private AnchorPane parent;

    @FXML
    private Button button1;

    @FXML
    private Button button2;

    @FXML
    private Button send;

    private int selfPort = -1;
    DatagramSocket socket = null;



    @FXML
    private void initialize() {
        System.out.println("Hello World!");

        // parent.setOnDragEntered(event -> System.out.println("setOnDragEntered"));
        // parent.setOnDragExited(event -> System.out.println("setOnDragExited"));

        parent.setOnDragOver(event -> {
            // System.out.println("setOnDragOver");
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                // 必须设置模式才能触发 setOnDragDropped
                event.acceptTransferModes(TransferMode.ANY);
            }
        });

        parent.setOnDragDropped(event -> {
            // System.out.println("setOnDragDropped");
            Dragboard dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                try {
                    File file = dragboard.getFiles().get(0);
                    if (file != null) {
                        System.out.println(file.getAbsolutePath());
                        sendDate(file);
                    }
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        });

        button1.setOnMouseClicked(event -> {
            System.out.println("click1");
            bindUdp(9001);
        });

        button2.setOnMouseClicked(event -> {
            System.out.println("click2");
            bindUdp(9002);
        });

        send.setOnMouseClicked(event -> {
            sendDate("");
        });
    }

    private void bindUdp(final int port) {

        if (selfPort > 0) {
            return;
        }

        selfPort = port;

        Thread thread = new Thread(() -> {
            try {

                socket = new DatagramSocket(port);

                byte[] data = new byte[1024];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                System.out.println("****服务器端已经启动，等待客户端发送数据");

                while (true) {
                    socket.receive(packet);
                    Message.convert(packet.getAddress().getHostAddress(), data);
                }


            } catch (Exception e) {
                System.err.println("error -> " + e.getMessage());
            } finally {
                if (socket != null) {
                    socket.close();
                }
                selfPort = -1;
            }
        });

        thread.start();
    }

    private void sendDate(String message) {
        if (socket == null) {
            System.out.println("socket 未连接");
        }

        if (selfPort <= 0) {
            System.out.println("端口未占用");
        }

        try {
            InetAddress address = InetAddress.getByName("localhost");
            int port = 9001 + 9002 - selfPort;
            byte[] data = "用户名：admin;密码：123".getBytes();
            // 2.创建数据报，包含发送的数据信息
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            // 4.向服务器端发送数据报
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("error -> " + e.getMessage());
        }
    }

    private void sendDate(File file) {
        if (socket == null) {
            System.out.println("socket 未连接");
        }

        if (selfPort <= 0) {
            System.out.println("端口未占用");
        }

        Message.send(socket, file, "localhost", 9001 + 9002 - selfPort);
    }


}
