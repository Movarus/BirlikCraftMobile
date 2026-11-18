package net.kdt.pojavlaunch.birlik;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Small Modrinth client for the BirlikCraft resource pack / shader browser. */
public final class BirlikModrinth {
    private static final String API = "https://api.modrinth.com/v2";
    public static final int PAGE_SIZE = 20;

    private BirlikModrinth() {}

    public static final class Pack {
        public String id, title, description, author, iconUrl;
        public long downloads;
    }

    public static final class PackVersion {
        public String name, gameVersions, url, sha1, fileName;
        @NonNull @Override public String toString() { return name + "  ·  " + gameVersions; }
    }

    /** Search packs of a type ("resourcepack" or "shader"), optionally for one Minecraft version and category. */
    public static List<Pack> search(@NonNull String projectType, @Nullable String mcVersion,
                                    @Nullable String category, @Nullable String query, int offset) throws IOException {
        StringBuilder facets = new StringBuilder("[[\"project_type:").append(projectType).append("\"]");
        if (mcVersion != null) facets.append(",[\"versions:").append(mcVersion).append("\"]");
        if (category != null) facets.append(",[\"categories:").append(category).append("\"]");
        facets.append("]");
        String url = API + "/search?limit=" + PAGE_SIZE + "&offset=" + offset
                + "&index=" + (query == null || query.isEmpty() ? "downloads" : "relevance")
                + "&facets=" + enc(facets.toString())
                + (query == null || query.isEmpty() ? "" : "&query=" + enc(query));
        JsonObject response = Tools.GLOBAL_GSON.fromJson(DownloadUtils.downloadString(url), JsonObject.class);
        List<Pack> packs = new ArrayList<>();
        if (response == null || !response.has("hits")) return packs;
        for (JsonElement element : response.getAsJsonArray("hits")) {
            JsonObject hit = element.getAsJsonObject();
            Pack pack = new Pack();
            pack.id = str(hit, "project_id");
            pack.title = str(hit, "title");
            pack.description = str(hit, "description");
            pack.author = str(hit, "author");
            pack.iconUrl = str(hit, "icon_url");
            JsonElement downloads = hit.get("downloads");
            pack.downloads = downloads == null || downloads.isJsonNull() ? 0 : downloads.getAsLong();
            if (pack.id != null) packs.add(pack);
        }
        return packs;
    }

    /** Long description of a project, with Markdown/HTML reduced to plain text. */
    public static String fetchDescription(@NonNull String projectId) throws IOException {
        JsonObject project = Tools.GLOBAL_GSON.fromJson(
                DownloadUtils.downloadString(API + "/project/" + projectId), JsonObject.class);
        String body = project == null ? null : str(project, "body");
        return body == null ? "" : plainText(body);
    }

    /** Downloadable versions of a project, newest first; filtered to a Minecraft version if given. */
    public static List<PackVersion> fetchVersions(@NonNull String projectId, @Nullable String mcVersion) throws IOException {
        String url = API + "/project/" + projectId + "/version"
                + (mcVersion == null ? "" : "?game_versions=" + enc("[\"" + mcVersion + "\"]"));
        JsonArray versions = Tools.GLOBAL_GSON.fromJson(DownloadUtils.downloadString(url), JsonArray.class);
        List<PackVersion> result = new ArrayList<>();
        if (versions == null) return result;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            JsonObject file = primaryFile(version.getAsJsonArray("files"));
            if (file == null) continue;
            PackVersion pv = new PackVersion();
            pv.name = str(version, "version_number");
            if (pv.name == null) pv.name = str(version, "name");
            JsonArray gameVersions = version.getAsJsonArray("game_versions");
            pv.gameVersions = summarizeVersions(gameVersions);
            pv.url = str(file, "url");
            pv.fileName = str(file, "filename");
            JsonObject hashes = file.getAsJsonObject("hashes");
            pv.sha1 = hashes == null ? null : str(hashes, "sha1");
            if (pv.url != null && pv.fileName != null) result.add(pv);
        }
        return result;
    }

    /** Download a pack file into a folder, verifying its SHA-1 when Modrinth provides one. */
    public static File install(@NonNull PackVersion version, @NonNull File folder,
                               @NonNull Tools.DownloaderFeedback feedback) throws IOException {
        FileUtils.ensureDirectory(folder);
        File target = new File(folder, version.fileName.replaceAll("[\\\\/:*?\"<>|\\t\\n]", "_"));
        byte[] buffer = new byte[8192];
        DownloadUtils.ensureSha1(target, version.sha1, () -> {
            DownloadUtils.downloadFileMonitored(version.url, target, buffer, feedback);
            return null;
        });
        return target;
    }

    public static String formatDownloads(long downloads) {
        if (downloads >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", downloads / 1_000_000d);
        if (downloads >= 1_000) return String.format(Locale.ROOT, "%.1fK", downloads / 1_000d);
        return Long.toString(downloads);
    }

    // ------------------------------------------------------------------------------------------

    private static String summarizeVersions(@Nullable JsonArray versions) {
        if (versions == null || versions.size() == 0) return "";
        String first = versions.get(0).getAsString();
        String last = versions.get(versions.size() - 1).getAsString();
        return versions.size() == 1 ? first : first + " – " + last;
    }

    @Nullable
    private static JsonObject primaryFile(@Nullable JsonArray files) {
        if (files == null || files.size() == 0) return null;
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            JsonElement primary = file.get("primary");
            if (primary != null && !primary.isJsonNull() && primary.getAsBoolean()) return file;
        }
        return files.get(0).getAsJsonObject();
    }

    static String plainText(String markdown) {
        return markdown
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")          // images
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")        // links -> text
                .replaceAll("(?s)<[^>]+>", "")                        // html tags
                .replaceAll("(?m)^#{1,6}\\s*", "")                    // headers
                .replaceAll("(\\*\\*|__|~~|`)", "")                   // emphasis, code
                .replaceAll("(?m)^\\s*[-*+]\\s+", "• ")               // bullets
                .replaceAll("(?m)^\\s*-{3,}\\s*$", "")                // rules
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    @Nullable
    private static String str(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String enc(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }
}
