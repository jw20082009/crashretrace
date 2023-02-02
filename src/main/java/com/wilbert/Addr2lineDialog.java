package com.wilbert;

import com.github.javaparser.utils.Pair;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.List;

public class Addr2lineDialog extends DialogWrapper {

    Project mProject;
    HashMap<String, Pair<List<StackTrace>, List<File>>> mStackMap;
    HashMap<String, Box> mBoxMap;
    JTextArea mRetraceArea;

    protected Addr2lineDialog(@Nullable Project project, HashMap<String, Pair<List<StackTrace>, List<File>>> stackTraces) {
        super(project, true);
        mProject = project;
        mStackMap = stackTraces;
        init();
    }

    private String getNdkPath(Project project) {
        String result = null;
        File localProperties = new File(project.getBasePath(), "local.properties");
        if (localProperties.exists()) {
            BufferedReader input = null;
            try {
                input = new BufferedReader(new InputStreamReader(new FileInputStream(localProperties)));
                String line = null;
                while ((line = input.readLine()) != null) {
                    if (line.trim().startsWith("ndk.dir")) {
                        String[] dir = line.split("=");
                        if (dir.length <= 0) {
                            return null;
                        }
                        result = dir[dir.length - 1];
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return result;
    }

    private String getAddr2Line(Project project, StackTrace.CPU type) {
        String ndkPath = getNdkPath(project);
        StringBuffer sb = new StringBuffer(ndkPath);
        sb.append("/toolchains/").append(type == StackTrace.CPU.CPU_64 ? "aarch64-linux-android-4.9": "arm-linux-androideabi-4.9")
                .append("/prebuilt/").append(EPlatform.Windows.isCurrentPlatform? "windows-x86_64/bin/": "darwin-x86_64/bin/")
                .append(type == StackTrace.CPU.CPU_64 ? "aarch64-linux-android-addr2line":"arm-linux-androideabi-addr2line");
        return sb.toString();
    }

    @Override
    protected @NotNull JPanel createButtonsPanel(@NotNull List<? extends JButton> buttons) {
        Iterator<? extends JButton> it = buttons.iterator();
        while (it.hasNext()) {
            JButton button = it.next();
            if (button.getText().equals("Cancel")) {
                it.remove();
            }
        }
        return super.createButtonsPanel(buttons);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JFrame jf = new JFrame();
        jf.setLocationRelativeTo(null);             // 把窗口位置设置到屏幕中心
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // 当点击窗口的关闭按钮时退出程序（没有这一句，程序不会退出）
        JPanel panel = new JPanel();
        panel.setLayout(new VerticalFlowLayout());
        Iterator<Map.Entry<String, Pair<List<StackTrace>, List<File>>>> it = mStackMap.entrySet().iterator();
        String addr2Line = null;
        while(it.hasNext()) {
            Map.Entry<String, Pair<List<StackTrace>, List<File>>> entry = it.next();
            List<File> soFiles = entry.getValue().b;
            List<StackTrace> stackTraces = entry.getValue().a;
            if (stackTraces.size() <= 0) {
                continue;
            }
            if (addr2Line == null) {
                addr2Line = getAddr2Line(mProject, stackTraces.get(0).mCPUType);
                Box box = getFileBox(Arrays.asList(addr2Line));
                panel.add(box);
                putBox("addr2line", box);
            }
            String soPath = "";
            if (soFiles != null && soFiles.size() > 0) {
                List<String> pathList = new ArrayList<>();
                for(File f: soFiles) {
                    pathList.add(f.getAbsolutePath());
                }
                Box box = getFileBox(pathList);
                panel.add(box);
                putBox(entry.getKey(), box);
            }
        }
        mRetraceArea = new JTextArea();
        mRetraceArea.setColumns(50);
        mRetraceArea.setLineWrap(true);
        mRetraceArea.setRows(10);
        panel.add(mRetraceArea);
        refreshStackTrace();
        return panel;
    }

    private void putBox(String key, Box box) {
        if (key == null || key.isEmpty() || box == null) {
            return;
        }
        if (mBoxMap == null) {
            mBoxMap = new HashMap<>();
        }
        mBoxMap.put(key, box);
    }

    protected void refreshStackTrace() {
        if (mBoxMap == null || mBoxMap.size() <= 1) {
            return;
        }
        String addr2Line = null;
        Box addr2lineBox = mBoxMap.get("addr2line");
        if (addr2lineBox != null) {
            JLabel label = (JLabel) addr2lineBox.getComponent(0);
            addr2Line = label.getText().trim();
        }
        StringBuffer sb = new StringBuffer();
        Iterator<Map.Entry<String, Box>> it = mBoxMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, Box> entry = it.next();
            String key = entry.getKey();
            if (key.equals("addr2line")) {
                continue;
            }
            List<StackTrace> stackTraces = mStackMap.get(key).a;
            if (stackTraces == null) {
                return;
            }
            Box box = entry.getValue();
            JLabel label = (JLabel) box.getComponent(0);
            String soPath = label.getText().trim();
            sb.append(execAddr2Line(addr2Line, soPath, stackTraces));
        }
        mRetraceArea.setText(sb.toString());
    }

    protected Box getFileBox(List<String> pathList) {
        if (pathList == null || pathList.size() <= 0) {
            return null;
        }
        Box box = Box.createHorizontalBox();
        JLabel label = new JLabel(pathList.get(0));
        box.add(label);
        box.add(Box.createHorizontalGlue());
        int pathSize = pathList.size();
        if (pathSize > 1) {
            JButton nextBtn = new JButton(pathSize +"(" +1 +")");
            nextBtn.setPreferredSize(new Dimension(40, 30));
            nextBtn.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String currentLabel = label.getText();
                    int currentIndex = 0;
                    for(int i = 0;i < pathSize; i ++) {
                        String filePath = pathList.get(i);
                        if (filePath.equals(currentLabel)) {
                            currentIndex = i;
                            break;
                        }
                    }
                    int index = (currentIndex + 1)%pathSize;
                    nextBtn.setText(pathList.size() +"(" + (index + 1) +")");
                    label.setText(pathList.get(index));
                    refreshStackTrace();
                }
            });
            box.add(nextBtn);
        }
        JButton button = new JButton("...");
        button.setPreferredSize(new Dimension(40, 30));
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, true, false, true);
                VirtualFile virtualFile = FileChooser.chooseFile(descriptor, mProject, null);
                String path = virtualFile.getCanonicalPath();
                label.setText(path);
                refreshStackTrace();
            }
        });
        box.add(button);
        return box;
    }

    protected String execAddr2Line(String addr2Line, String targetSo, List<StackTrace> stackTraces) {
        StringBuffer sb = new StringBuffer();
        sb.append(addr2Line).append(" -e ").append(targetSo).append(" -a");
        for(StackTrace stackTrace: stackTraces) {
            sb.append(" ").append(stackTrace.stack());
        }
        StringBuffer result = new StringBuffer();
        BufferedReader input = null;
        try {
            String cmd = sb.toString();
            System.out.println(cmd);
            Process process = Runtime.getRuntime().exec(cmd);
            input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ((line = input.readLine()) != null) {
                result.append(line).append("\n");
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result.toString();
    }
}
