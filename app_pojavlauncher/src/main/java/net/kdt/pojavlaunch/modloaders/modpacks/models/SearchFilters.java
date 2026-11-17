package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
    /** Modrinth project type: "modpack", "mod", "resourcepack" or "shader". Null falls back to isModpack. */
    @Nullable public String projectType;

}
