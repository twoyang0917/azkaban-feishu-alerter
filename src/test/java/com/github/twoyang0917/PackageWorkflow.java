package com.github.twoyang0917;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class PackageWorkflow {
    public static void main(String[] args) {
        // 指定要打包的文件夹和输出的 ZIP 文件路径
        String folderToZip = "src/test/java/workflow";  // 要打包的文件夹
        String zipFilePath = "target/workflow.zip";  // 输出的 ZIP 文件路径

        try {
            zipFolder(folderToZip, zipFilePath);
            System.out.println("文件夹打包成功！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void zipFolder(String folderPath, String zipFilePath) throws IOException {
        // 创建 ZIP 输出流
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
            Path sourceFolder = Paths.get(folderPath);

            // 遍历文件夹
            Files.walk(sourceFolder).forEach(path -> {
                try {
                    // 为每个文件和子文件夹创建 ZIP 条目
                    String zipEntryName = sourceFolder.relativize(path).toString().replace("\\", "/");

                    // 如果是文件夹，则跳过
                    if (Files.isDirectory(path)) {
                        return;
                    }

                    // 创建 ZIP 条目并写入文件
                    zos.putNextEntry(new ZipEntry(zipEntryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
