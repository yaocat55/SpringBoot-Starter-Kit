package com.quick.springbootlombok.util;

import lombok.Cleanup;
import lombok.SneakyThrows;

import java.io.*;

/**
 * CleanupExample —— 演示 @Cleanup 自动关闭资源。
 * 等价于 try-with-resources，但更简洁。
 */
public class CleanupExample {

    @SneakyThrows
    public static String readFirstLine(File file) {
        @Cleanup FileInputStream fis = new FileInputStream(file);
        @Cleanup InputStreamReader isr = new InputStreamReader(fis);
        @Cleanup BufferedReader reader = new BufferedReader(isr);
        return reader.readLine();
    }
}
