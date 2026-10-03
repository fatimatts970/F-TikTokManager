package com.ftiktokmanager.app;

public class CloneModel {
    public int id;
    public String name;
    public String cookies;
    public String vcamPath;
    public int vcamOn;
    public long lastOpened;
    public String notes = "";
    public String email = "";
    public String username = "";
    public String password = "";
    public int desk;
    public int pxOn;
    public String pxHost = "";
    public int pxPort;
    public String pxUser = "";
    public String pxPass = "";

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

    public CloneModel(int id, String name, String cookies, String vcamPath, int vcamOn, long lastOpened,
                      String notes, String email, String username, String password,
                      int desk, int pxOn, String pxHost, int pxPort, String pxUser, String pxPass) {
        this(id, name, cookies, vcamPath, vcamOn, lastOpened);
        this.notes = notes == null ? "" : notes;
        this.email = email == null ? "" : email;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.desk = desk;
        this.pxOn = pxOn;
        this.pxHost = pxHost == null ? "" : pxHost;
        this.pxPort = pxPort;
        this.pxUser = pxUser == null ? "" : pxUser;
        this.pxPass = pxPass == null ? "" : pxPass;
    }

    public boolean proxyActive() {
        return pxOn == 1 && !pxHost.isEmpty() && pxPort > 0;
    }
}
