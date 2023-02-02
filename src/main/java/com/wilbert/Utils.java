package com.wilbert;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static List<File> searchFile(String searchName, File dir) {
        File[] subFolders = dir.listFiles(new FileFilter() {// 运用内部匿名类获得文件
            @Override
            public boolean accept(File pathname) {// 实现FileFilter类的accept方法
                if (pathname.isDirectory()
                        || (pathname.isFile() && pathname.getName().equals(searchName)))// 目录或文件包含关键字
                    return true;
                return false;
            }
        });
        List<File> result = new ArrayList<>();
        for ( int i = 0; i < subFolders.length; i ++ ) {
            if (subFolders[i].isFile()) {
                result.add(subFolders[i]);
            } else {
                List<File> foldResult = searchFile(searchName, subFolders[i]);
                for (int j = 0; j < foldResult.size(); j++) {// 循环显示文件
                    result.add(foldResult.get(j));// 文件保存到集合中
                }
            }
        }
        return result;
    }

    public static String getSysClipboardText() {
        String ret = "";
        Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 获取剪切板中的内容
        Transferable clipTf = sysClip.getContents(null);
        if (clipTf != null) {
            // 检查内容是否是文本类型
            if (clipTf.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    ret = (String) clipTf
                            .getTransferData(DataFlavor.stringFlavor);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return ret;
    }
}
