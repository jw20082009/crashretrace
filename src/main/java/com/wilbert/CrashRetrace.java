package com.wilbert;

import com.github.javaparser.utils.Pair;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrashRetrace extends AnAction {

    HashMap<String, Pair<List<StackTrace>, List<File>>> mStackMap = new HashMap<>();

    @Override
    public void actionPerformed(AnActionEvent e) {
        // TODO: insert action logic here
        Project project = e.getData(PlatformDataKeys.PROJECT);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        System.out.println("CrashRetrace actionPerformed");
        if (EPlatform.Windows.isCurrentPlatform) {
            System.out.println("currentPlatForm is " + EPlatform.Windows);
        }
        if (editor == null||project==null)
            return;
        SelectionModel selectionModel = editor.getSelectionModel();
        mStackMap.clear();
        String text = "";
        if (selectionModel.hasSelection()) {
            text = selectionModel.getSelectedText();
        } else {
            text = Utils.getSysClipboardText();
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        initStackMapByTrace(project, text);
        Addr2lineDialog dialog = new Addr2lineDialog(project, mStackMap);
        dialog.show();
    }

    /**
     * 读取崩溃堆栈——堆栈地址/崩溃so路径/崩溃库的CPU架构(arm64/armeabi-v7a)
     * @param project
     * @param text
     */
    private void initStackMapByTrace(Project project, String text) {
        String basePath = project.getBasePath();
        String[] textList = text.split("\n");
        for(String s:textList) {
            StackTrace stackTrace = new StackTrace();
            int res = stackTrace.parseStack(s);
            if (res < 1) {
                System.out.println("parseStack,"+ res+" Failed:" + s);
            } else {
                System.out.println(stackTrace);
                Pair<List<StackTrace>, List<File>> traceList = mStackMap.get(stackTrace.mTargetLib);
                if (traceList == null) {
                    List<StackTrace> traces = new ArrayList<>();
                    List<File> sortedFiles = new ArrayList<>();
                    sortInsert(stackTrace.mCPUType, Utils.searchFile(stackTrace.mTargetLib, new File(basePath)), sortedFiles);
                    traces.add(stackTrace);
                    traceList = new Pair<>(traces, sortedFiles);
                    mStackMap.put(stackTrace.mTargetLib, traceList);
                } else
                    traceList.a.add(stackTrace);
            }
        }
    }

    /**
     * 按照
     * @param cpu
     * @param files
     * @param sortedFiles
     */
    public void sortInsert(StackTrace.CPU cpu, List<File> files, List<File> sortedFiles) {
        if (files == null || files.size() <= 0) {
            return;
        }
        sortedFiles.clear();
        for (File f : files) {
            insertBy(cpu, f, sortedFiles);
        }
    }

    /**
     * 匹配最后一个/armxxxx/xxxx.so，从而得到
     * @param path
     * @return
     */
    private String getLastArmFlag(String path) {
        Pattern p = Pattern.compile("\\S*(\\/arm\\S+)(?!.*\\1)(\\/\\S+\\.so)");
        Matcher m = p.matcher(path);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private void insertBy(StackTrace.CPU cpu, File f, List<File> sortedList) {
        int size = sortedList.size();
        if (size <= 0) {
            sortedList.add(f);
            return;
        }
        String key = "/" + (cpu == StackTrace.CPU.CPU_64 ?"arm64-v8a": "armeabi-v7a");
        size = sortedList.size();
        for ( int i = 0; i< size; i++) {
            File path = sortedList.get(i);
            if (getLastArmFlag(f.getAbsolutePath()).equals(key)) {//如果被插入的路径arm标记是堆栈所要求的
                if (!getLastArmFlag(path.getAbsolutePath()).equals(key)) { //且当前路径不是堆栈所要求的，则把被插入路径插入到当前路径前面
                    sortedList.add(i, f);
                    break;
                } else if (i == size - 1){
                    sortedList.add(f);
                    break;
                } else if (path.lastModified() - 1000 * 60 <= f.lastModified() && path.length() < f.length()){ //如果被插入路径大小大于当前路径 或者被插入路径修改时间大于当前路径，则把被插入路径插入到当前路径前面
                    sortedList.add(i, f);
                    break;
                } else if (path.lastModified() - 1000 * 60 > f.lastModified() && path.lastModified() < f.lastModified()){
                    sortedList.add(i, f);
                    break;
                } else {
                    continue;
                }
            } else {
                sortedList.add(f);
                break;
            }
        }
    }

}
