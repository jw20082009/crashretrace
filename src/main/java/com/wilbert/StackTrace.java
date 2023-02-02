package com.wilbert;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StackTrace {
    enum CPU {
        UNKNOWN, CPU_16, CPU_32, CPU_64;
    }

    CPU mCPUType = CPU.UNKNOWN;
    String mStack;
    String mTargetLib;
    String mReTrace;

    public StackTrace() {
    }

    public String stack() {
        return mStack;
    }

    public String targetLib() {
        return mTargetLib;
    }

    public String retrace() {
        return mReTrace;
    }

    public void setRetrace(String retrace) {
        mReTrace = retrace;
    }

    public int parseStack(String line) {
        if (line == null || line.isEmpty()) {
            return -1;
        }
        Pattern p = Pattern.compile("#\\d+\\spc\\s(\\S+)\\s+\\S+(\\/)(?!.*\\1)(\\S+\\.so)\\s");
        Matcher m = p.matcher(line);
        int result = -1;
        if (m.find()) {
            int count = m.groupCount();
            switch (count) {
                case 3:
                    if (setTargetLib(m.group(3))) {
                        result++;
                    }
                case 1:
                    if (setStack(m.group(1))) {
                        result++;
                    }
                    break;
                case 2:
                    return -1;
            }
        }
        return result;
    }

    private boolean setTargetLib(String lib) {
        if (lib == null || lib.isEmpty() || !lib.contains("so")) {
            return false;
        }
        mTargetLib = lib;
        return true;
    }

    private boolean setStack(String stack) {
        if (stack == null || stack.isEmpty()) {
            System.out.println("setStack empty");
            return false;
        }
        int length = stack.length() * 4;
        switch (length) {
            case 16:
                mCPUType = CPU.CPU_16;
                break;
            case 32:
                mCPUType = CPU.CPU_32;
                break;
            case 64:
                mCPUType = CPU.CPU_64;
                break;
            default:
                mCPUType = CPU.UNKNOWN;
        }
        if (mCPUType == CPU.UNKNOWN) {
            mStack = "";
            return false;
        }
        mStack = stack;
        return true;
    }

    @Override
    public String toString() {
        return "StackTrace{" + mCPUType +
                ", " + mStack +
                ", " + mTargetLib +
                ", " + mReTrace +
                '}';
    }
}
