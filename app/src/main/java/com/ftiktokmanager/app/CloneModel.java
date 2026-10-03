package com.ftiktokmanager.app;

public class CloneModel {
    public int id;
    public String name;
    public String cookies;
    public String vcamPath;
    public int vcamOn;
    public long lastOpened;

    public CloneModel(int id, String name, String cookies, String vcamPath, int vcamOn) {
        this(id, name, cookies, vcamPath, vcamOn, 0L);
    }

    public CloneModel(int id, String name, String cookies, String vcamPath, int vcamOn, long lastOpened) {
        this.id = id;
        this.name = name;
        this.cookies = cookies == null ? "" : cookies;
        this.vcamPath = vcamPath == null ? "" : vcamPath;
        this.vcamOn = vcamOn;
        this.lastOpened = lastOpened;
    }
}
