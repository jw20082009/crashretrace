package com.wilbert;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    public interface SearchListener{
        void onSearch(String fileName);
    }

    public static List<String> searchFile(String searchName, File dir, int depth, String keyWords, int keyWordsDepth, SearchListener listener) {
        // 运用内部匿名类获得文件
        File[] subFolders = dir.listFiles(pathname -> {// 实现FileFilter类的accept方法
            boolean isDirectory = pathname.isDirectory();
            if (isDirectory) {
                listener.onSearch(pathname.getAbsolutePath());
            }
            if (isDirectory || (pathname.isFile() && pathname.getName().equals(searchName)))// 目录或文件包含关键字
                return true;
            return false;
        });
        List<String> result = new ArrayList<>();
        depth++;
        for ( int i = 0; i < subFolders.length; i ++ ) {
            if (subFolders[i].isFile()) {
                result.add(subFolders[i].getAbsolutePath());
            } else if (depth < keyWordsDepth || subFolders[i].getAbsolutePath().contains(keyWords)){
                List<String> foldResult = searchFile(searchName, subFolders[i], depth, keyWords, keyWordsDepth, listener);
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
