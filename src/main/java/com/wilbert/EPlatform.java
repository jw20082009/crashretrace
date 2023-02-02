package com.wilbert;

public enum EPlatform {
    Any("any"),
    Linux("Linux", "linux"),
    Mac_OS("Mac OS", "mac", "os", "x"),
    Mac_OS_X("Mac OS X", "mac", "os", "x"),
    Windows("Windows", "windows"),
    OS2("OS/2", "os/2"),
    Solaris("Solaris", "solaris"),
    SunOS("SunOS", "sunos"),
    MPEiX("MPE/iX", "mpe/ix"),
    HP_UX("HP-UX", "hp-ux"),
    AIX("AIX", "aix"),
    OS390("OS/390", "os/390"),
    FreeBSD("FreeBSD", "freebsd"),
    Irix("Irix", "irix"),
    Digital_Unix("Digital Unix", "digital", "unix"),
    NetWare_411("NetWare", "netware"),
    OSF1("OSF1", "osf1"),
    OpenVMS("OpenVMS", "openvms"),
    Others("Others");

    private String OS;
    public Boolean isCurrentPlatform = true;

    EPlatform(String desc, String... keys){
        this.description = desc;
        OS = System.getProperty("os.name").toLowerCase();
        for(String k: keys) {
            isCurrentPlatform = isCurrentPlatform && OS.indexOf(k) >= 0;
        }
    }

    public String toString(){
        return description;
    }

    private String description;

}
