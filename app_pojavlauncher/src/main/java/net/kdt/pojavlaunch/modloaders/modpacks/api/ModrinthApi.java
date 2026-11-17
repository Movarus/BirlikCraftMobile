package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    public static final String TYPE_MODPACK = "modpack";
    public static final String TYPE_RESOURCE_PACK = "resourcepack";
    public static final String TYPE_SHADER = "shader";

    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        String projectType = searchFilters.projectType != null
                ? searchFilters.projectType
                : (searchFilters.isModpack ? TYPE_MODPACK : "mod");
        facetString.append(String.format("[\"project_type:%s\"]", projectType));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            String hitType = getStringOrNull(hit, "project_type");
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    TYPE_MODPACK.equals(hitType),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    getStringOrNull(hit, "description"),
                    getStringOrNull(hit, "icon_url")
            );
            // The search endpoint can report shaders under a different type; trust our filter
            items[i].projectType = projectType;
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            mcNames[i] = (gameVersions != null && gameVersions.size() > 0) ? gameVersions.get(0).getAsString() : "";
            JsonObject file = getPrimaryFile(version.getAsJsonArray("files"));
            urls[i] = file.get("url").getAsString();
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = file.has("hashes") ? file.get("hashes").getAsJsonObject() : null;
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        return new ModDetail(item, names, mcNames, urls, hashes);
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        if (isPackType(modDetail.projectType)) {
            installPackFile(modDetail, selectedVersion);
            return null;
        }
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    @Override
    public void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        if (!isPackType(modDetail.projectType)) {
            ModpackApi.super.handleInstallation(context, modDetail, selectedVersion);
            return;
        }
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File installed = installPackFile(modDetail, selectedVersion);
                int message = TYPE_SHADER.equals(modDetail.projectType)
                        ? R.string.birlik_shader_installed
                        : R.string.birlik_resourcepack_installed;
                Tools.runOnUiThread(() -> Toast.makeText(context,
                        context.getString(message, installed.getName()), Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            }
        });
    }

    private static boolean isPackType(String projectType) {
        return TYPE_RESOURCE_PACK.equals(projectType) || TYPE_SHADER.equals(projectType);
    }

    /**
     * Download a resource pack or shader pack into the current profile's
     * resourcepacks/ or shaderpacks/ folder.
     */
    private static File installPackFile(ModDetail modDetail, int selectedVersion) throws IOException {
        String folderName = TYPE_SHADER.equals(modDetail.projectType) ? "shaderpacks" : "resourcepacks";
        File packDir = new File(getCurrentGameDir(), folderName);
        if (!packDir.isDirectory() && !packDir.mkdirs()) {
            throw new IOException("Unable to create " + packDir.getAbsolutePath());
        }

        String url = modDetail.versionUrls[selectedVersion];
        String sha1 = modDetail.versionHashes[selectedVersion];
        File outputFile = new File(packDir, fileNameFromUrl(url, modDetail.title));
        try {
            byte[] buffer = new byte[8192];
            DownloadUtils.ensureSha1(outputFile, sha1, (Callable<Void>) () -> {
                DownloadUtils.downloadFileMonitored(url, outputFile, buffer,
                        new DownloaderProgressWrapper(R.string.birlik_pack_downloading,
                                ProgressLayout.INSTALL_MODPACK));
                return null;
            });
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
        return outputFile;
    }

    private static File getCurrentGameDir() {
        try {
            return Tools.getGameDirPath(LauncherProfiles.getCurrentProfile());
        } catch (RuntimeException e) {
            return new File(Tools.DIR_GAME_NEW);
        }
    }

    private static String fileNameFromUrl(String url, String fallbackTitle) {
        String name = Uri.decode(url.substring(url.lastIndexOf('/') + 1));
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        if (name.isEmpty()) name = fallbackTitle;
        name = name.replaceAll("[\\\\/:*?\"<>|\\t\\n]", "_");
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) name += ".zip";
        return name;
    }

    private static JsonObject getPrimaryFile(JsonArray files) {
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            JsonElement primary = file.get("primary");
            if (primary != null && !primary.isJsonNull() && primary.getAsBoolean()) return file;
        }
        return files.get(0).getAsJsonObject();
    }

    private static String getStringOrNull(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return (element == null || element.isJsonNull()) ? null : element.getAsString();
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            
            ModDownloader modDownloader = new ModDownloader(instanceDestination);
            for(ModrinthIndex.ModrinthIndexFile indexFile : modrinthIndex.files) {
                modDownloader.submitDownload(indexFile.fileSize, indexFile.path, indexFile.hashes.sha1, indexFile.downloads);
            }
            modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }
}
