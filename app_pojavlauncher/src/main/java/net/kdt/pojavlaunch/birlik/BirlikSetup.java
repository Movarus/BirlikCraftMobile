package net.kdt.pojavlaunch.birlik;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftLauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * BirlikCraft server setup.
 *
 * <ul>
 *     <li>The launcher only offers two fixed Fabric profiles: Minecraft 26.2 and 26.3.</li>
 *     <li>Before every launch, Fabric and a curated set of performance mods are installed
 *     from Fabric's and Modrinth's official APIs.</li>
 *     <li>The launch is refused if the mods folder contains any jar that BirlikCraft did not install.</li>
 *     <li>Game and launcher settings are tuned once for the highest FPS.</li>
 * </ul>
 */
public final class BirlikSetup {
    private static final String TAG = "BirlikSetup";

    public static final String PROFILE_26_2 = "birlikcraft-26-2";
    public static final String PROFILE_26_3 = "birlikcraft-26-3";
    public static final String DEFAULT_PROFILE = PROFILE_26_3;

    /** Profile key -> Minecraft version. Order is the order shown to the player. */
    private static final Map<String, String> GAME_VERSIONS = new LinkedHashMap<>();
    static {
        GAME_VERSIONS.put(PROFILE_26_3, "26.3");
        GAME_VERSIONS.put(PROFILE_26_2, "26.2");
    }

    /**
     * Modrinth slugs of the FPS mods BirlikCraft installs. Required dependencies (such as
     * Fabric API) are resolved automatically. Sodium is deliberately left out: Amethyst does
     * not support it and it causes graphical issues and crashes on Android renderers.
     */
    private static final String[] PERFORMANCE_MODS = {
            "lithium",        // game logic / tick optimisations
            "ferrite-core",   // memory usage
            "modernfix",      // faster startup, lower memory
            "entityculling",  // skip rendering hidden entities and block entities
            "immediatelyfast" // faster immediate-mode rendering (HUD, text, items)
    };

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    private static final String OPTIONS_FLAG = ".birlikcraft-fps-options-v1";
    private static final String LAUNCHER_DEFAULTS_FLAG = "birlik_perf_defaults_v1";

    /** Profile whose setup finished and that may launch exactly once. */
    private static volatile String sReadyProfile;
    private static volatile boolean sRunning;

    private BirlikSetup() {}

    public static boolean isBirlikProfile(@Nullable String profileKey) {
        return profileKey != null && GAME_VERSIONS.containsKey(profileKey);
    }

    @Nullable
    public static String getGameVersion(@Nullable String profileKey) {
        return profileKey == null ? null : GAME_VERSIONS.get(profileKey);
    }

    public static Set<String> getProfileKeys() {
        return GAME_VERSIONS.keySet();
    }

    // ------------------------------------------------------------------------------------------
    // Fixed profiles
    // ------------------------------------------------------------------------------------------

    /**
     * Keep exactly the two BirlikCraft profiles. Any other profile is removed and the fixed
     * fields (name, game directory) are restored if they were changed.
     * @return whether the profile list was modified
     */
    public static boolean enforceProfiles(@NonNull MinecraftLauncherProfiles launcherProfiles) {
        boolean modified = false;
        Iterator<String> keys = launcherProfiles.profiles.keySet().iterator();
        while (keys.hasNext()) {
            if (!isBirlikProfile(keys.next())) {
                keys.remove();
                modified = true;
            }
        }
        for (Map.Entry<String, String> entry : GAME_VERSIONS.entrySet()) {
            String key = entry.getKey();
            String mcVersion = entry.getValue();
            MinecraftProfile profile = launcherProfiles.profiles.get(key);
            if (profile == null) {
                profile = new MinecraftProfile();
                // Placeholder until Fabric is installed by prepare()
                profile.lastVersionId = mcVersion;
                launcherProfiles.profiles.put(key, profile);
                modified = true;
            }
            String name = "BirlikCraft " + mcVersion + " (Fabric)";
            String gameDir = "./instances/" + key;
            if (!name.equals(profile.name)) { profile.name = name; modified = true; }
            if (!gameDir.equals(profile.gameDir)) { profile.gameDir = gameDir; modified = true; }
            if (profile.lastVersionId == null || !(profile.lastVersionId.equals(mcVersion)
                    || isFabricVersionOf(profile.lastVersionId, mcVersion))) {
                profile.lastVersionId = mcVersion;
                modified = true;
            }
        }
        return modified;
    }

    private static boolean isFabricVersionOf(String versionId, String mcVersion) {
        return versionId.startsWith("fabric-loader-") && versionId.endsWith("-" + mcVersion);
    }

    // ------------------------------------------------------------------------------------------
    // Launcher performance defaults
    // ------------------------------------------------------------------------------------------

    /** Apply launcher settings tuned for the highest FPS, once per install. */
    public static void applyLauncherDefaults(@NonNull SharedPreferences prefs) {
        if (prefs.getBoolean(LAUNCHER_DEFAULTS_FLAG, false)) return;
        prefs.edit()
                // Do not cap CPU/GPU clocks to avoid throttling: we want peak FPS
                .putBoolean("sustainedPerformance", false)
                // VSync caps FPS at the display refresh rate
                .putBoolean("force_vsync", false)
                .putBoolean("vsync_in_zink", false)
                // Run the game on the fast CPU cores
                .putBoolean("bigCoreAffinity", true)
                // Render at 70% of the screen resolution: much less GPU work, still sharp on phones
                .putInt("resolutionRatio", 70)
                .putBoolean(LAUNCHER_DEFAULTS_FLAG, true)
                .apply();
    }

    // ------------------------------------------------------------------------------------------
    // Launch preparation
    // ------------------------------------------------------------------------------------------

    /**
     * @return true if {@link #prepareAsync} finished for this profile; the flag is consumed so
     * every launch runs the setup (and the mod check) again.
     */
    public static boolean consumeReady(String profileKey) {
        if (profileKey != null && profileKey.equals(sReadyProfile)) {
            sReadyProfile = null;
            return true;
        }
        return false;
    }

    /** Run {@link #prepare} in the background, then trigger the launch again. */
    public static void prepareAsync(@NonNull Activity activity, @NonNull String profileKey) {
        if (sRunning) return;
        sRunning = true;
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.birlik_setup_progress,
                activity.getString(R.string.birlik_setup_step_start));
        PojavApplication.sExecutorService.execute(() -> {
            boolean success = false;
            try {
                prepare(activity, profileKey);
                success = true;
            } catch (ModGuardException e) {
                Tools.runOnUiThread(() -> showModGuardDialog(activity, e.getForbiddenMods()));
            } catch (Exception e) {
                Log.e(TAG, "BirlikCraft setup failed", e);
                Tools.showErrorRemote(activity, R.string.birlik_setup_failed, e);
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
                sRunning = false;
            }
            if (success) {
                sReadyProfile = profileKey;
                Tools.runOnUiThread(() -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
            }
        });
    }

    /** Install Fabric and the performance mods, apply FPS options and verify the mods folder. */
    public static void prepare(@NonNull Activity activity, @NonNull String profileKey) throws Exception {
        String mcVersion = getGameVersion(profileKey);
        if (mcVersion == null) throw new IllegalArgumentException("Not a BirlikCraft profile: " + profileKey);
        LauncherProfiles.load();
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
        if (profile == null) throw new IllegalStateException("Missing profile " + profileKey);
        File gameDir = Tools.getGameDirPath(profile);
        FileUtils.ensureDirectory(gameDir);

        // 1. Fabric loader
        if (!isFabricVersionOf(profile.lastVersionId, mcVersion) || !isVersionInstalled(profile.lastVersionId)) {
            progress(activity, 5, R.string.birlik_setup_step_fabric);
            String versionId = installFabric(mcVersion);
            LauncherProfiles.load();
            profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
            profile.lastVersionId = versionId;
            LauncherProfiles.write();
        }

        // 2. Server list, BirlikCraft resource pack and FPS options
        progress(activity, 20, R.string.birlik_setup_step_options);
        copyServerDefaults(gameDir);
        applyFpsOptions(gameDir);

        // 3. Performance mods
        File modsDir = new File(gameDir, "mods");
        FileUtils.ensureDirectory(modsDir);
        File stateFile = getStateFile(profileKey);
        Map<String, String> allowed = readState(stateFile);
        try {
            progress(activity, 30, R.string.birlik_setup_step_mods);
            List<ModFile> mods = resolvePerformanceMods(mcVersion);
            Map<String, String> fresh = new LinkedHashMap<>();
            int index = 0;
            for (ModFile mod : mods) {
                progress(activity, 30 + (60 * index++ / Math.max(1, mods.size())), R.string.birlik_setup_step_mod_file, mod.fileName);
                File target = new File(modsDir, mod.fileName);
                DownloadUtils.ensureSha1(target, mod.sha1, () -> {
                    DownloadUtils.downloadFile(mod.url, target);
                    return null;
                });
                fresh.put(mod.fileName, mod.sha1);
            }
            // Remove BirlikCraft mods that were replaced by a newer version
            if (allowed != null) {
                for (Map.Entry<String, String> old : allowed.entrySet()) {
                    if (fresh.containsKey(old.getKey())) continue;
                    File oldFile = new File(modsDir, old.getKey());
                    if (oldFile.isFile() && Tools.compareSHA1(oldFile, old.getValue()) && !oldFile.delete()) {
                        Log.w(TAG, "Could not delete outdated mod " + oldFile);
                    }
                }
            }
            allowed = fresh;
            writeState(stateFile, allowed);
        } catch (IOException e) {
            // Offline: keep using the mods installed last time, if any
            if (allowed == null) throw e;
            Log.w(TAG, "Could not update performance mods, using the installed ones", e);
        }

        // 4. Refuse to launch with any other mod
        progress(activity, 95, R.string.birlik_setup_step_check);
        verifyMods(modsDir, allowed);
    }

    /** Double check right before the JVM starts. */
    public static void verifyModsBeforeLaunch(@Nullable String profileKey, @NonNull File gameDir) throws ModGuardException {
        if (!isBirlikProfile(profileKey)) return;
        Map<String, String> allowed = readState(getStateFile(profileKey));
        verifyMods(new File(gameDir, "mods"), allowed == null ? new LinkedHashMap<>() : allowed);
    }

    private static void verifyMods(File modsDir, Map<String, String> allowed) throws ModGuardException {
        File[] files = modsDir.listFiles();
        if (files == null) return;
        List<String> forbidden = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) continue;
            String name = file.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            // Fabric only loads .jar files from the mods folder
            if (!lower.endsWith(".jar")) continue;
            String sha1 = allowed.get(name);
            if (sha1 == null || !Tools.compareSHA1(file, sha1)) forbidden.add(name);
        }
        if (!forbidden.isEmpty()) throw new ModGuardException(forbidden);
    }

    private static void showModGuardDialog(Activity activity, List<String> mods) {
        if (activity.isFinishing()) return;
        StringBuilder list = new StringBuilder();
        for (String mod : mods) list.append("\n• ").append(mod);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.birlik_mod_guard_title)
                .setMessage(activity.getString(R.string.birlik_mod_guard_message, list.toString()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ------------------------------------------------------------------------------------------
    // Fabric
    // ------------------------------------------------------------------------------------------

    private static boolean isVersionInstalled(String versionId) {
        return new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json").isFile();
    }

    private static String installFabric(String mcVersion) throws IOException {
        JsonArray loaders = Tools.GLOBAL_GSON.fromJson(
                DownloadUtils.downloadString(FABRIC_META + "/versions/loader/" + mcVersion), JsonArray.class);
        if (loaders == null || loaders.size() == 0) {
            throw new IOException("Fabric does not support Minecraft " + mcVersion + " yet");
        }
        String loaderVersion = null;
        for (JsonElement element : loaders) {
            JsonObject loader = element.getAsJsonObject().getAsJsonObject("loader");
            if (loader == null) continue;
            if (loaderVersion == null) loaderVersion = loader.get("version").getAsString();
            JsonElement stable = loader.get("stable");
            if (stable != null && stable.getAsBoolean()) {
                loaderVersion = loader.get("version").getAsString();
                break;
            }
        }
        if (loaderVersion == null) throw new IOException("No Fabric loader found for " + mcVersion);

        String profileJson = DownloadUtils.downloadString(
                FABRIC_META + "/versions/loader/" + mcVersion + "/" + loaderVersion + "/profile/json");
        JsonObject parsed = Tools.GLOBAL_GSON.fromJson(profileJson, JsonObject.class);
        String versionId = parsed.get("id").getAsString();
        File versionDir = new File(Tools.DIR_HOME_VERSION, versionId);
        FileUtils.ensureDirectory(versionDir);
        Tools.write(new File(versionDir, versionId + ".json").getAbsolutePath(), profileJson);
        return versionId;
    }

    // ------------------------------------------------------------------------------------------
    // Modrinth
    // ------------------------------------------------------------------------------------------

    private static final class ModFile {
        final String fileName, url, sha1;
        ModFile(String fileName, String url, String sha1) {
            this.fileName = fileName; this.url = url; this.sha1 = sha1;
        }
    }

    private static List<ModFile> resolvePerformanceMods(String mcVersion) throws IOException {
        List<ModFile> result = new ArrayList<>();
        Set<String> seenProjects = new HashSet<>();
        List<String> queue = new ArrayList<>();
        for (String slug : PERFORMANCE_MODS) queue.add(slug);
        while (!queue.isEmpty()) {
            String project = queue.remove(0);
            JsonObject version = findLatestVersion(project, mcVersion);
            if (version == null) {
                Log.w(TAG, "No Fabric build of " + project + " for Minecraft " + mcVersion + ", skipping");
                continue;
            }
            String projectId = version.get("project_id").getAsString();
            if (!seenProjects.add(projectId)) continue;
            JsonObject file = getPrimaryFile(version.getAsJsonArray("files"));
            if (file == null) continue;
            JsonObject hashes = file.getAsJsonObject("hashes");
            if (hashes == null || hashes.get("sha1") == null) continue; // cannot verify, do not install
            result.add(new ModFile(sanitizeFileName(file.get("filename").getAsString()),
                    file.get("url").getAsString(), hashes.get("sha1").getAsString()));

            JsonArray dependencies = version.getAsJsonArray("dependencies");
            if (dependencies == null) continue;
            for (JsonElement element : dependencies) {
                JsonObject dependency = element.getAsJsonObject();
                JsonElement type = dependency.get("dependency_type");
                JsonElement depProject = dependency.get("project_id");
                if (type == null || depProject == null || depProject.isJsonNull()) continue;
                if ("required".equals(type.getAsString()) && !seenProjects.contains(depProject.getAsString())) {
                    queue.add(depProject.getAsString());
                }
            }
        }
        if (result.isEmpty()) throw new IOException("No performance mods available for Minecraft " + mcVersion);
        return result;
    }

    @Nullable
    private static JsonObject findLatestVersion(String project, String mcVersion) throws IOException {
        String url = MODRINTH_API + "/project/" + project + "/version"
                + "?loaders=" + URLEncoder.encode("[\"fabric\"]", "UTF-8")
                + "&game_versions=" + URLEncoder.encode("[\"" + mcVersion + "\"]", "UTF-8");
        JsonArray versions;
        try {
            versions = Tools.GLOBAL_GSON.fromJson(DownloadUtils.downloadString(url), JsonArray.class);
        } catch (IOException e) {
            // Modrinth answers 404 for unknown projects; treat other failures as offline
            if (isHttpNotFound(e)) return null;
            throw e;
        }
        if (versions == null || versions.size() == 0) return null;
        // Newest first; prefer a release over beta/alpha builds
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            JsonElement type = version.get("version_type");
            if (type != null && "release".equals(type.getAsString())) return version;
        }
        return versions.get(0).getAsJsonObject();
    }

    private static boolean isHttpNotFound(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains("HTTP 404")) return true;
        }
        return false;
    }

    @Nullable
    private static JsonObject getPrimaryFile(@Nullable JsonArray files) {
        if (files == null || files.size() == 0) return null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            JsonElement primary = file.get("primary");
            if (primary != null && !primary.isJsonNull() && primary.getAsBoolean()) return file;
        }
        return files.get(0).getAsJsonObject();
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\t\\n]", "_");
    }

    // ------------------------------------------------------------------------------------------
    // Game defaults
    // ------------------------------------------------------------------------------------------

    private static void copyServerDefaults(File gameDir) {
        try {
            File servers = new File(gameDir, "servers.dat");
            File defaultServers = new File(Tools.DIR_GAME_NEW, "servers.dat");
            if (!servers.exists() && defaultServers.isFile()) {
                org.apache.commons.io.FileUtils.copyFile(defaultServers, servers);
            }
            File pack = new File(gameDir, "resourcepacks/BirlikCraft.zip");
            File defaultPack = new File(Tools.DIR_GAME_NEW, "resourcepacks/BirlikCraft.zip");
            if (!pack.exists() && defaultPack.isFile()) {
                org.apache.commons.io.FileUtils.copyFile(defaultPack, pack);
            }
        } catch (IOException e) {
            Log.w(TAG, "Could not copy BirlikCraft defaults", e);
        }
    }

    /** Write options tuned for FPS the first time an instance is prepared. */
    private static void applyFpsOptions(File gameDir) throws IOException {
        File flag = new File(gameDir, OPTIONS_FLAG);
        if (flag.exists()) return;
        MCOptionUtils.load(gameDir.getAbsolutePath());
        MCOptionUtils.set("maxFps", "260");               // "Unlimited"
        MCOptionUtils.set("enableVsync", "false");
        MCOptionUtils.set("graphicsMode", "0");           // Fast
        MCOptionUtils.set("renderDistance", "8");
        MCOptionUtils.set("simulationDistance", "5");
        MCOptionUtils.set("entityDistanceScaling", "0.75");
        MCOptionUtils.set("renderClouds", "\"false\"");
        MCOptionUtils.set("particles", "2");              // Minimal
        MCOptionUtils.set("entityShadows", "false");
        MCOptionUtils.set("ao", "false");                 // Smooth lighting off
        MCOptionUtils.set("biomeBlendRadius", "0");
        MCOptionUtils.set("mipmapLevels", "0");
        MCOptionUtils.set("prioritizeChunkUpdates", "0");
        MCOptionUtils.set("menuBackgroundBlurriness", "0");
        MCOptionUtils.set("lang", "tr_tr");
        MCOptionUtils.set("soundCategory_music", "0.0");
        MCOptionUtils.set("tutorialStep", "none");
        MCOptionUtils.set("onboardAccessibility", "false");
        MCOptionUtils.set("skipMultiplayerWarning", "true");
        MCOptionUtils.set("joinedFirstServer", "true");
        MCOptionUtils.save();
        if (!flag.createNewFile()) Log.w(TAG, "Could not create " + flag);
    }

    // ------------------------------------------------------------------------------------------
    // Allowed-mods state (kept in the launcher's private data, not in the game folder)
    // ------------------------------------------------------------------------------------------

    private static File getStateFile(String profileKey) {
        return new File(Tools.DIR_DATA, "birlikcraft/" + profileKey + "-mods.json");
    }

    @Nullable
    private static Map<String, String> readState(File stateFile) {
        if (!stateFile.isFile()) return null;
        try {
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            Map<String, String> map = Tools.GLOBAL_GSON.fromJson(Tools.read(stateFile), type);
            return map;
        } catch (Exception e) {
            Log.w(TAG, "Corrupted mod state " + stateFile, e);
            return null;
        }
    }

    private static void writeState(File stateFile, Map<String, String> allowed) throws IOException {
        FileUtils.ensureParentDirectory(stateFile);
        Tools.write(stateFile.getAbsolutePath(), Tools.GLOBAL_GSON.toJson(allowed));
    }

    private static void progress(Activity activity, int percent, int stepRes, Object... args) {
        String step = activity.getString(stepRes, args);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, percent, R.string.birlik_setup_progress, step);
    }

    /** Thrown when the mods folder contains mods that BirlikCraft did not install. */
    public static final class ModGuardException extends Exception {
        private final List<String> mForbiddenMods;
        public ModGuardException(List<String> forbiddenMods) {
            super("Mods not allowed on BirlikCraft: " + forbiddenMods);
            mForbiddenMods = forbiddenMods;
        }
        public List<String> getForbiddenMods() { return mForbiddenMods; }
    }
}
