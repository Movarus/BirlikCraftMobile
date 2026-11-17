package net.kdt.pojavlaunch.modloaders.modpacks.models;

public abstract class ModSource {
    public int apiSource;
    public boolean isModpack;
    /** Modrinth project type ("modpack", "resourcepack", "shader"...). */
    public String projectType = "modpack";
}
