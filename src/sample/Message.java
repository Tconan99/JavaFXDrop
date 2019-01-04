package sample;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Message {

    public static final int TYPE_START = 1;
    public static final int TYPE_MIDDLE = 2;
    public static final int TYPE_END = 3;

    public int length = 0;
    public int type = TYPE_START;
    public int code = 0;
    public String fileName = "";
    public byte[] content;

    public static final Map<String, String> map = new HashMap<>();

    public static byte[] start(int code, String fileName) {
        byte[] typeByte = toBytes(TYPE_START);
        byte[] codeByte = toBytes(code);
        byte[] fileNameByte = fileName.getBytes();
        byte[] lengthByte = toBytes(4 + 4 + 4 + fileNameByte.length);
        byte[] messageByte = new byte[1024];
        System.arraycopy(lengthByte, 0, messageByte, 0, 4);
        System.arraycopy(typeByte, 0, messageByte, 4, 4);
        System.arraycopy(codeByte, 0, messageByte, 8, 4);
        System.arraycopy(fileNameByte, 0, messageByte, 12, fileNameByte.length);
        return messageByte;
    }

    public static byte[] middle(int code, byte[] fileByte) {
        byte[] typeByte = toBytes(TYPE_MIDDLE);
        byte[] codeByte = toBytes(code);
        byte[] lengthByte = toBytes(4 + 4 + 4 + fileByte.length);
        byte[] messageByte = new byte[1024];
        System.arraycopy(lengthByte, 0, messageByte, 0, 4);
        System.arraycopy(typeByte, 0, messageByte, 4, 4);
        System.arraycopy(codeByte, 0, messageByte, 8, 4);
        System.arraycopy(fileByte, 0, messageByte, 12, fileByte.length);
        return messageByte;
    }

    public static byte[] end(int code) {
        byte[] typeByte = toBytes(TYPE_END);
        byte[] codeByte = toBytes(code);
        byte[] lengthByte = toBytes(4 + 4 + 4);
        byte[] messageByte = new byte[1024];
        System.arraycopy(lengthByte, 0, messageByte, 0, 4);
        System.arraycopy(typeByte, 0, messageByte, 4, 4);
        System.arraycopy(codeByte, 0, messageByte, 8, 4);
        return messageByte;
    }

    public static Message convert(String ip, byte[] bytes) {
        Message message = new Message();
        message.length = toInt(Arrays.copyOfRange(bytes, 0, 4));
        message.type = toInt(Arrays.copyOfRange(bytes, 4, 8));
        message.code = toInt(Arrays.copyOfRange(bytes, 8, 12));

        if (message.type == TYPE_START) {
            message.fileName = new String(Arrays.copyOfRange(bytes, 12, message.length));

            // 拼接完整路径
            FileSystemView fsv = FileSystemView.getFileSystemView();
            File desktop = fsv.getHomeDirectory();
            message.fileName = desktop.getPath() + File.separator + message.fileName;

            File file = new File(message.fileName);
            String baseFileName = message.fileName;
            int index = 1;
            while (file.exists()) {
                int lastDot = baseFileName.lastIndexOf(".");
                if (lastDot < 0) {
                    break;
                }

                String name = baseFileName.substring(0, lastDot) + "(" + (index ++) + ")" + baseFileName.substring(lastDot);
                file = new File(name);
            }
            message.fileName = file.getAbsolutePath();
            System.out.println("fileName -> " + message.fileName);
            map.put(ip + message.code, message.fileName);

            File tempFile = new File(message.fileName + ".tmp");

            try {

                if (tempFile.exists()) {
                    if (tempFile.delete()) {
                        System.out.println("删除文件成功");
                    } else {
                        System.err.println("删除文件失败");
                    }
                }

                if (tempFile.createNewFile()) {
                    System.out.println("创建文件成功");
                } else {
                    System.err.println("创建文件失败");
                }

            } catch (Exception e) {
                System.err.println("err -> " + e);
            }
        } else if (message.type == TYPE_MIDDLE) {
            message.content = Arrays.copyOfRange(bytes, 12, message.length);
            // System.out.println("length -> " + message.content.length);

            String fileName = map.get(ip + message.code);
            File tempFile = new File(fileName + ".tmp");
            try (FileOutputStream stream = new FileOutputStream(tempFile, true)) {
                stream.write(message.content);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }

        } else if (message.type == TYPE_END) {
            String fileName = map.get(ip + message.code);
            System.out.println("end -> " + fileName);
            File tempFile = new File(fileName + ".tmp");
            File file = new File(fileName);
            if (tempFile.renameTo(file)) {
                System.out.println("文件重命名成功");
            } else {
                System.err.println("文件重命名失败");
            }

            map.remove(ip + message.code);
        }

        return message;
    }

    private static int totalCode = 1;

    private static int getCode() {
        return totalCode++;
    }


    public static void send(DatagramSocket socket, File file, String ip, int port) {

        if (socket == null) {
            System.out.println("socket 未连接");
            return;
        }

        if (file == null) {
            System.out.println("文件不能为空");
            return;
        }

        if (!file.isFile()) {
            System.out.println("不是文件");
            return;
        }

        if (!file.exists()) {
            System.out.println("文件不存在");
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                int code = getCode();
                InetAddress address = InetAddress.getByName(ip);

                // start
                byte[] startByte = start(code, file.getName());
                DatagramPacket startPacket = new DatagramPacket(startByte, startByte.length, address, port);
                socket.send(startPacket);

                // middle
                long length = file.length();
                long count = length / 512 + (length % 512 > 0 ? 1 : 0);
                byte[] content = new byte[512];

                System.out.println("total -> " + count);
                try (FileInputStream inputStream = new FileInputStream(file)) {

                    int sendedCount = 0;
                    while (inputStream.available() > 0) {
                        int size = inputStream.read(content);
                        if (size > 0) {
                            byte[] real = new byte[size];
                            // System.out.println("sended -> " + size);
                            sendedCount ++;
                            System.arraycopy(content, 0, real, 0, size);
                            byte[] middleByte = middle(code, real);
                            DatagramPacket middlePacket = new DatagramPacket(middleByte, middleByte.length, address, port);
                            socket.send(middlePacket);
                            Thread.sleep(5);
                        }
                    }
                    System.out.println("sended -> " + sendedCount);

                } catch (Exception e) {
                    System.err.println("error -> " + e.getMessage());
                }

                // end
                byte[] endByte = end(code);
                DatagramPacket endPacket = new DatagramPacket(endByte, endByte.length, address, port);
                socket.send(endPacket);

                System.out.println("send -> end");

            } catch (Exception e) {
                System.err.println("error -> " + e.getMessage());
            }
        });

        thread.start();

    }

    private static int toInt(byte[] bytes) {
        if (bytes.length < 4) {
            return 0;
        }
        int result = 0;
        result += bytes[0] << 24;
        result += bytes[1] << 16;
        result += bytes[2] << 8;
        result += bytes[3] /*<< 0*/;
        return result;
    }

    private static byte[] toBytes(int i) {
        byte[] result = new byte[4];
        result[0] = (byte) (i >> 24);
        result[1] = (byte) (i >> 16);
        result[2] = (byte) (i >> 8);
        result[3] = (byte) (i /*>> 0*/);
        return result;
    }
}
