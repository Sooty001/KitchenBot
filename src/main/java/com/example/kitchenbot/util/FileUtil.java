package com.example.kitchenbot.util;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileUtil {

    public static File saveShoppingList(String content) {
        try {
            File file = File.createTempFile("shopping_list_", ".txt");
            String header = "СПИСОК ПОКУПОК (" + LocalDateTime.now().format(DateTimeFormatter.ISO_DATE) + ")\n----------------------\n";
            Files.writeString(file.toPath(), header + content);
            return file;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File downloadFile(String fileUrl, String outputName) {
        try {
            File temp = new File(System.getProperty("java.io.tmpdir"), outputName);
            try (InputStream is = new URL(fileUrl).openStream();
                 FileOutputStream fos = new FileOutputStream(temp)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) fos.write(buffer, 0, len);
            }
            return temp;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка скачивания: " + e.getMessage());
        }
    }
}