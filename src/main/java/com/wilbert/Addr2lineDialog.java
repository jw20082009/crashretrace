package com.wilbert;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Addr2lineDialog extends DialogWrapper {

    Project mProject;
    HashMap<String, List<StackTrace>> mStackMap;
    HashMap<String, List<String>> mLibPathMap = new HashMap<>();
    HashMap<String, Box> mBoxMap = new HashMap<>();
    JTextArea mRetraceArea;
    List<DepthPath> mBasePathList = new ArrayList<>();

    class DepthPath{
        int depth = 0;
        String path = "";
        String keywords = "";

        public DepthPath(int depth, String path, String keywords) {
            this.depth = depth;
            this.path = path;
            this.keywords = keywords;
        }
    }

    protected Addr2lineDialog(@Nullable Project project, HashMap<String, List<StackTrace>> stackTraces) {
        super(project, true);
        mProject = project;
        mStackMap = stackTraces;
        DepthPath buildPath = new DepthPath(4, project.getBasePath(), "intermediates");
        DepthPath crashPath = new DepthPath(2, project.getBasePath(), "crashRetrace");
        mBasePathList.add(buildPath);
        mBasePathList.add(crashPath);
        System.out.println("Addr2lineDialog construct");
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
    protected @Nullable JComponent createCenterPanel() {
        JFrame jf = new JFrame();
        jf.setLocationRelativeTo(null);             // 把窗口位置设置到屏幕中心
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE); // 当点击窗口的关闭按钮时退出程序（没有这一句，程序不会退出）
        JPanel panel = new JPanel();
        panel.setLayout(new VerticalFlowLayout());
        String addr2Line = null;
        mLibPathMap.clear();
        Iterator<Map.Entry<String, List<StackTrace>>> it = mStackMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, List<StackTrace>> entry = it.next();
            List<StackTrace> stackTraces = entry.getValue();
            String libName = entry.getKey();
            if (stackTraces.size() <= 0) {
                continue;
            }
            if (addr2Line == null) {
                String key = "addr2line";
                addr2Line = getAddr2Line(mProject, stackTraces.get(0).mCPUType);
                mLibPathMap.put(key, Arrays.asList(addr2Line));
                refreshFileBox(key);
                panel.add(mBoxMap.get(key));
            }
            refreshFileBox(libName);
            panel.add(mBoxMap.get(libName));
        }
        mRetraceArea = new JTextArea();
        mRetraceArea.setColumns(50);
        mRetraceArea.setLineWrap(true);
        mRetraceArea.setRows(10);
        panel.add(mRetraceArea);
        refreshStackTrace();
        new Thread(() -> {
            if (mStackMap != null) {
                Iterator<Map.Entry<String, List<StackTrace>>> it1 = mStackMap.entrySet().iterator();
                while(it1.hasNext()) {
                    Map.Entry<String, List<StackTrace>> entry = it1.next();
                    String key = entry.getKey();
                    refreshLibPath(key);
                    SwingUtilities.invokeLater(() -> {
                        String tempLib = key;
                        refreshFileBox(tempLib);
                        refreshStackTrace();
                    });
                }
            }
        }).start();
        return panel;
    }

    protected void refreshStackTrace() {
        System.out.println("refreshStackTrace");
        if (mBoxMap == null || mBoxMap.size() <= 1) {
            if (mRetraceArea != null) {
                mRetraceArea.setText("mBoxMap == null || mBoxMap.size() <= 1");
            }
            return;
        }

        //获取addr2line地址
        String addr2Line = null;
        Box addr2lineBox = mBoxMap.get("addr2line");
        if (addr2lineBox != null) {
            JTextField label = (JTextField) addr2lineBox.getComponent(0);
            addr2Line = label.getText().trim();
        }

        //根据stacktrace，使用addr2line和libpath解析堆栈，如果没有libpath则直接显示原堆栈
        StringBuffer sb = new StringBuffer();
        Iterator<Map.Entry<String, Box>> it = mBoxMap.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, Box> entry = it.next();
            String key = entry.getKey();
            if (key.equals("addr2line")) {
                continue;
            }
            List<StackTrace> stackTraces = mStackMap.get(key);
            if (stackTraces == null) {
                return;
            }
            Box box = entry.getValue();
            JTextField label = (JTextField) box.getComponent(0);
            String soPath = label.getText().trim();
            if (new File(soPath).isFile()) {
                sb.append(execAddr2Line(addr2Line, soPath, stackTraces));
            } else {
                for (StackTrace stackTrace: stackTraces) {
                    sb.append(stackTrace.mStack).append(" ").append(stackTrace.mTargetLib).append("\n");
                }
            }
        }
        mRetraceArea.setText(sb.toString());
    }

    protected void refreshFileBox(String lib) {
        System.out.println("refreshFileBox0:"+lib);
        if (lib == null || lib.length() <= 0) {
            System.out.println("refreshFileBox lib empty");
            if (mRetraceArea != null) {
                mRetraceArea.setText("refreshFileBox lib empty");
            }
            return;
        }
        System.out.println("refreshFileBox1:"+lib);
        int totalSize = 0;
        int currentIndex = -1;
        String pathValue = lib;
        List<String> pathList = mLibPathMap.get(lib);
        if (pathList != null && pathList.size() > 0) {
            totalSize = pathList.size();
            pathValue = pathList.get(0);
        }
        System.out.println("refreshFileBox2:"+lib);
        Box box = mBoxMap.get(lib);
        if (box == null) {
            System.out.println("refreshFileBox3:"+lib);
            box = Box.createHorizontalBox();
            JTextField label = new JTextField(pathValue);
            label.setEditable(false);
            box.add(label);
            box.add(Box.createHorizontalGlue());
            JButton nextBtn = new JButton((currentIndex + 1) +"/" +totalSize);
            nextBtn.setPreferredSize(new Dimension(40, 30));
            nextBtn.addActionListener(e -> {
                refreshFileBox(lib);
                refreshStackTrace();
            });
            box.add(nextBtn);
            JButton button = new JButton(lib.equals("addr2line")? "...": "reTrace");
            button.setPreferredSize(new Dimension(80, 30));
            button.addActionListener(e -> {
                String text = button.getText();
                if (text.equals("...")) {
                    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, true, false, true);
                    VirtualFile virtualFile = FileChooser.chooseFile(descriptor, mProject, null);
                    if (virtualFile != null) {
                        String path = virtualFile.getCanonicalPath();
                        label.setText(path);
                        refreshStackTrace();
                    }
                } else {
                    label.setText(lib);
                    new Thread(() -> {
                        String tempLib = lib;
                        mFileIndex = 0;
                        refreshLibPath(tempLib);
                        SwingUtilities.invokeLater(() -> {
                            refreshFileBox(tempLib);
                            refreshStackTrace();
                        });
                    }).start();
                }
            });
            box.add(button);
            mBoxMap.put(lib, box);
        } else {
            if (totalSize <= 0 || pathList == null) {
                return;
            }
            JTextField pathLabel = (JTextField) box.getComponent(0);
            JButton nextBtn = (JButton) box.getComponent(2);
            pathValue = pathLabel.getText();
            for (int i = 0; i< totalSize; i++) {
                String filePath = pathList.get(i);
                if (filePath.equals(pathValue)) {
                    currentIndex = i;
                    break;
                }
            }
            int index = (currentIndex + 1) % totalSize;
            nextBtn.setText((index + 1) + "/" + totalSize);
            String text = pathList.get(index);
            pathLabel.setText(text);
        }
    }

    private void refreshLibPath(String libName) {
        System.out.println("refreshLibPath:"+libName);
        if (mStackMap == null || mStackMap.get(libName) == null) {
            System.out.println("refreshLibPath:" + libName +" failed for without stackTrace");
            if (mRetraceArea != null) {
                mRetraceArea.setText("refreshLibPath:" + libName +" failed for without stackTrace");
            }
            return;
        }

        System.out.println("refreshLibPath:" + libName);
        StackTrace.CPU cpu = mStackMap.get(libName).get(0).mCPUType;
        List<String> list = mLibPathMap.get(libName);
        if (list == null) {
            list = new ArrayList<>();
        }
        list.clear();
        for (DepthPath path : mBasePathList) {
            List<String> sortedFile = new ArrayList<>();
            File file = new File(path.path);
            if (file.exists() && file.isDirectory()) {
                sortInsert(cpu, Utils.searchFile(libName, file, 0, path.keywords, path.depth, mListener), sortedFile);
                list.addAll(sortedFile);
            }
        }
        mLibPathMap.put(libName, list);
    }

    long mFileIndex = 0;
    StringBuffer stringBuffer = null;
    Object mLock = new Object();
    Utils.SearchListener mListener = fileName -> {
        mFileIndex++;
        synchronized (mLock) {
            stringBuffer = new StringBuffer("searching:").append(mFileIndex).append("\n");
            stringBuffer.append(fileName).append("\n");
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                synchronized (mLock) {
                    if (mRetraceArea != null  && stringBuffer != null) {
                        mRetraceArea.setText(stringBuffer.toString());
                    }
                }
            }
        });
    };

    /**
     * 按照
     * @param cpu
     * @param files
     * @param sortedFiles
     */
    public void sortInsert(StackTrace.CPU cpu, List<String> files, List<String> sortedFiles) {
        if (files == null || files.size() <= 0) {
            return;
        }
        sortedFiles.clear();
        for (String f : files) {
            insertBy(cpu, f, sortedFiles);
        }
    }

    private void insertBy(StackTrace.CPU cpu, String f, List<String> sortedList) {
        int size = sortedList.size();
        if (size <= 0) {
            sortedList.add(f);
            return;
        }
        String key = "/" + (cpu == StackTrace.CPU.CPU_64 ?"arm64-v8a": "armeabi-v7a");
        size = sortedList.size();
        for ( int i = 0; i< size; i++) {
            String path = sortedList.get(i);
            File pathFile = new File(path);
            File file = new File(f);
            if (getLastArmFlag(f).equals(key)) {//如果被插入的路径arm标记是堆栈所要求的
                if (!getLastArmFlag(path).equals(key)) { //且当前路径不是堆栈所要求的，则把被插入路径插入到当前路径前面
                    sortedList.add(i, f);
                    break;
                } else if (i == size - 1){
                    sortedList.add(f);
                    break;
                } else if (pathFile.lastModified() - 1000 * 60 <= file.lastModified() && pathFile.length() < file.length()){ //如果被插入路径大小大于当前路径 或者被插入路径修改时间大于当前路径，则把被插入路径插入到当前路径前面
                    sortedList.add(i, f);
                    break;
                } else if (pathFile.lastModified() - 1000 * 60 > file.lastModified() && pathFile.lastModified() < file.lastModified()){
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
