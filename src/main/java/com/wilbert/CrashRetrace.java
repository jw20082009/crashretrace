package com.wilbert;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CrashRetrace extends AnAction {

    HashMap<String, List<StackTrace>> mStackMap = new HashMap<>();

    @Override
    public void actionPerformed(AnActionEvent e) {
        // TODO: insert action logic here
        Project project = e.getData(PlatformDataKeys.PROJECT);
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        System.out.println("CrashRetrace actionPerformed");
        if (EPlatform.Windows.isCurrentPlatform) {
            System.out.println("currentPlatForm is " + EPlatform.Windows);
        }
        if (project==null){
            System.out.println("CrashRetrace actionPerformed "+(editor == null? "editor null":"project null"));
            return;
        }
        mStackMap.clear();
        String text = "";
        if (editor == null) {
            text = Utils.getSysClipboardText();
        } else {
            SelectionModel selectionModel = editor.getSelectionModel();
            if (selectionModel.hasSelection()) {
                text = selectionModel.getSelectedText();
            } else {
                text = Utils.getSysClipboardText();
            }
        }

        if (text == null || text.isEmpty()) {
            System.out.println("CrashRetrace actionPerformed "+(text == null? "text null":"text empty"));
            return;
        }
        initStackMapByTrace(text);
        Addr2lineDialog dialog = new Addr2lineDialog(project, mStackMap);
        dialog.show();
    }

    /**
     * 读取崩溃堆栈——堆栈地址/崩溃so路径/崩溃库的CPU架构(arm64/armeabi-v7a)
     * @param text
     */
    private void initStackMapByTrace(String text) {
        String[] textList = text.split("\n");
        for(String s:textList) {
            StackTrace stackTrace = new StackTrace();
            int res = stackTrace.parseStack(s);
            if (res < 1) {
                System.out.println("parseStack,"+ res+" Failed:" + s);
            } else {
                System.out.println(stackTrace);
                List<StackTrace> traceList = mStackMap.get(stackTrace.mTargetLib);
                if (traceList == null) {
                    traceList = new ArrayList<>();
                    traceList.add(stackTrace);
                    mStackMap.put(stackTrace.mTargetLib, traceList);
                } else
                    traceList.add(stackTrace);
            }
        }
    }

}
