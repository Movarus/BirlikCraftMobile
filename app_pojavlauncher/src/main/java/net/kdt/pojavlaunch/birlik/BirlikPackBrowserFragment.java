package net.kdt.pojavlaunch.birlik;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * BirlikCraft resource pack / shader browser: category sidebar on the left, result cards in the
 * middle and a detail panel that slides in from the right with the description and install button.
 */
public class BirlikPackBrowserFragment extends Fragment {
    public static final String TAG = "BirlikPackBrowserFragment";
    public static final String ARG_PROJECT_TYPE = "project_type";
    public static final String TYPE_RESOURCE_PACK = "resourcepack";
    public static final String TYPE_SHADER = "shader";

    /** Modrinth category slug -> label. The first entry (null) is "All". */
    private static final String[][] RESOURCE_CATEGORIES = {
            {null, "Tümü"}, {"vanilla-like", "Vanilla benzeri"}, {"realistic", "Gerçekçi"},
            {"simplistic", "Sade"}, {"themed", "Temalı"}, {"combat", "PvP / Savaş"},
            {"tweaks", "İnce ayarlar"}, {"gui", "Arayüz"}, {"fonts", "Yazı tipi"},
            {"16x", "16x"}, {"32x", "32x"}, {"64x", "64x"}};
    private static final String[][] SHADER_CATEGORIES = {
            {null, "Tümü"}, {"vanilla-like", "Vanilla benzeri"}, {"semi-realistic", "Yarı gerçekçi"},
            {"realistic", "Gerçekçi"}, {"cartoon", "Çizgi film"}, {"fantasy", "Fantastik"},
            {"potato", "Çok zayıf cihaz"}, {"low", "Düşük donanım"}, {"medium", "Orta donanım"},
            {"high", "Yüksek donanım"}};

    private static final LruCache<String, Bitmap> ICONS = new LruCache<>(80);

    private String mProjectType = TYPE_RESOURCE_PACK;
    private String mMcVersion;
    private boolean mOnlyThisVersion = true;
    private String mCategory;
    private String mQuery = "";

    private final List<BirlikModrinth.Pack> mPacks = new ArrayList<>();
    private final PackAdapter mAdapter = new PackAdapter();
    private int mGeneration;
    private boolean mLoading, mEndReached;

    private View mLoadingView, mDetail;
    private TextView mEmpty, mVersionFilter;
    private BirlikModrinth.Pack mSelected;
    private List<BirlikModrinth.PackVersion> mSelectedVersions = new ArrayList<>();

    public BirlikPackBrowserFragment() {
        super(R.layout.fragment_birlik_packs);
    }

    public String getProjectType() { return mProjectType; }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null && TYPE_SHADER.equals(args.getString(ARG_PROJECT_TYPE))) mProjectType = TYPE_SHADER;
        String profile = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, BirlikSetup.DEFAULT_PROFILE);
        mMcVersion = BirlikSetup.getGameVersion(profile);

        mLoadingView = view.findViewById(R.id.birlik_packs_loading);
        mEmpty = view.findViewById(R.id.birlik_packs_empty);
        mDetail = view.findViewById(R.id.birlik_pack_detail);
        mVersionFilter = view.findViewById(R.id.birlik_packs_version_filter);

        EditText search = view.findViewById(R.id.birlik_packs_search);
        search.setHint(TYPE_SHADER.equals(mProjectType) ? R.string.birlik_search_shaders_hint
                : R.string.birlik_search_resourcepacks_hint);
        search.setOnEditorActionListener((v, actionId, event) -> {
            mQuery = v.getText().toString().trim();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus();
            reload();
            return actionId == EditorInfo.IME_ACTION_SEARCH;
        });

        buildCategories(view.findViewById(R.id.birlik_packs_categories));

        if (mMcVersion == null) mOnlyThisVersion = false;
        mVersionFilter.setVisibility(mMcVersion == null ? View.GONE : View.VISIBLE);
        mVersionFilter.setOnClickListener(v -> { mOnlyThisVersion = !mOnlyThisVersion; updateVersionFilter(); reload(); });
        updateVersionFilter();

        RecyclerView list = view.findViewById(R.id.birlik_packs_list);
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), 2);
        list.setLayoutManager(layoutManager);
        list.setAdapter(mAdapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= mPacks.size() - 4) loadMore();
            }
        });

        view.findViewById(R.id.birlik_pack_detail_close).setOnClickListener(v -> closeDetail());
        view.findViewById(R.id.birlik_pack_detail_install).setOnClickListener(v -> installSelected());

        reload();
    }

    // ------------------------------------------------------------------------------------------
    // Filters
    // ------------------------------------------------------------------------------------------

    private void buildCategories(LinearLayout container) {
        String[][] categories = TYPE_SHADER.equals(mProjectType) ? SHADER_CATEGORIES : RESOURCE_CATEGORIES;
        List<TextView> items = new ArrayList<>();
        int padH = getResources().getDimensionPixelSize(R.dimen._8sdp);
        int padV = getResources().getDimensionPixelSize(R.dimen._6sdp);
        for (String[] category : categories) {
            TextView item = new TextView(requireContext());
            item.setText(category[1]);
            item.setTextColor(getResources().getColor(R.color.primary_text));
            item.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen._10ssp));
            item.setPadding(padH, padV, padH, padV);
            item.setBackgroundResource(R.drawable.birlik_category_bg);
            item.setSelected(category[0] == null);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = getResources().getDimensionPixelSize(R.dimen._2sdp);
            container.addView(item, lp);
            items.add(item);
            item.setOnClickListener(v -> {
                for (TextView other : items) other.setSelected(other == v);
                mCategory = category[0];
                reload();
            });
        }
    }

    private void updateVersionFilter() {
        if (mMcVersion == null) return;
        mVersionFilter.setSelected(mOnlyThisVersion);
        mVersionFilter.setText(mOnlyThisVersion
                ? getString(R.string.birlik_packs_only_version, mMcVersion)
                : getString(R.string.birlik_packs_all_versions));
    }

    // ------------------------------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------------------------------

    private void reload() {
        mGeneration++;
        mPacks.clear();
        mAdapter.notifyDataSetChanged();
        mEndReached = false;
        mLoading = false;
        closeDetail();
        loadMore();
    }

    private void loadMore() {
        if (mLoading || mEndReached) return;
        mLoading = true;
        final int generation = mGeneration;
        final int offset = mPacks.size();
        final String version = mOnlyThisVersion ? mMcVersion : null;
        final String category = mCategory, query = mQuery, type = mProjectType;
        mLoadingView.setVisibility(View.VISIBLE);
        mEmpty.setVisibility(View.GONE);
        PojavApplication.sExecutorService.execute(() -> {
            List<BirlikModrinth.Pack> page = null;
            Exception error = null;
            try {
                page = BirlikModrinth.search(type, version, category, query, offset);
            } catch (Exception e) {
                error = e;
            }
            final List<BirlikModrinth.Pack> result = page;
            final Exception failure = error;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || generation != mGeneration) return;
                mLoading = false;
                mLoadingView.setVisibility(View.GONE);
                if (failure != null) {
                    mEmpty.setText(R.string.birlik_packs_error);
                    mEmpty.setVisibility(mPacks.isEmpty() ? View.VISIBLE : View.GONE);
                    return;
                }
                if (result.size() < BirlikModrinth.PAGE_SIZE) mEndReached = true;
                int start = mPacks.size();
                mPacks.addAll(result);
                mAdapter.notifyItemRangeInserted(start, result.size());
                if (mPacks.isEmpty()) {
                    mEmpty.setText(R.string.birlik_packs_empty);
                    mEmpty.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    // ------------------------------------------------------------------------------------------
    // Detail panel
    // ------------------------------------------------------------------------------------------

    private void openDetail(BirlikModrinth.Pack pack) {
        mSelected = pack;
        mSelectedVersions = new ArrayList<>();
        ((TextView) mDetail.findViewById(R.id.birlik_pack_detail_title)).setText(pack.title);
        ((TextView) mDetail.findViewById(R.id.birlik_pack_detail_meta)).setText(
                getString(R.string.birlik_packs_meta, pack.author, BirlikModrinth.formatDownloads(pack.downloads)));
        TextView description = mDetail.findViewById(R.id.birlik_pack_detail_description);
        description.setText(pack.description);
        loadIcon(mDetail.findViewById(R.id.birlik_pack_detail_icon), pack.iconUrl);
        Spinner versions = mDetail.findViewById(R.id.birlik_pack_detail_versions);
        versions.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_birlik_spinner,
                new String[]{getString(R.string.birlik_packs_loading_versions)}));
        View install = mDetail.findViewById(R.id.birlik_pack_detail_install);
        install.setEnabled(false);

        if (mDetail.getVisibility() != View.VISIBLE) {
            mDetail.setVisibility(View.VISIBLE);
            mDetail.post(() -> {
                mDetail.setTranslationX(mDetail.getWidth());
                mDetail.animate().translationX(0).setDuration(250).start();
            });
        }

        final String version = mOnlyThisVersion ? mMcVersion : null;
        PojavApplication.sExecutorService.execute(() -> {
            String body = null;
            List<BirlikModrinth.PackVersion> found = null;
            try { body = BirlikModrinth.fetchDescription(pack.id); } catch (Exception ignored) {}
            try { found = BirlikModrinth.fetchVersions(pack.id, version); } catch (Exception ignored) {}
            final String fullBody = body;
            final List<BirlikModrinth.PackVersion> versionList = found;
            Tools.runOnUiThread(() -> {
                if (!isAdded() || mSelected != pack) return;
                if (fullBody != null && !fullBody.isEmpty()) {
                    description.setText(pack.description + "\n\n" + fullBody);
                }
                if (versionList == null || versionList.isEmpty()) {
                    versions.setAdapter(new ArrayAdapter<>(requireContext(), R.layout.item_birlik_spinner,
                            new String[]{getString(R.string.birlik_packs_no_versions)}));
                    return;
                }
                mSelectedVersions = versionList;
                ArrayAdapter<BirlikModrinth.PackVersion> adapter =
                        new ArrayAdapter<>(requireContext(), R.layout.item_birlik_spinner, versionList);
                versions.setAdapter(adapter);
                install.setEnabled(true);
            });
        });
    }

    private void closeDetail() {
        mSelected = null;
        if (mDetail == null || mDetail.getVisibility() != View.VISIBLE) return;
        mDetail.animate().translationX(mDetail.getWidth()).setDuration(200)
                .withEndAction(() -> mDetail.setVisibility(View.GONE)).start();
    }

    private void installSelected() {
        if (mSelected == null || mSelectedVersions.isEmpty()) return;
        Spinner versions = mDetail.findViewById(R.id.birlik_pack_detail_versions);
        int index = Math.max(0, versions.getSelectedItemPosition());
        if (index >= mSelectedVersions.size()) return;
        BirlikModrinth.PackVersion version = mSelectedVersions.get(index);
        File folder = new File(getCurrentGameDir(), TYPE_SHADER.equals(mProjectType) ? "shaderpacks" : "resourcepacks");
        String title = mSelected.title;
        android.content.Context context = requireContext().getApplicationContext();
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.birlik_pack_downloading);
        Toast.makeText(context, getString(R.string.birlik_packs_installing, title), Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File file = BirlikModrinth.install(version, folder, (curr, max) -> ProgressLayout.setProgress(
                        ProgressLayout.INSTALL_MODPACK, max > 0 ? (int) (100f * curr / max) : 0,
                        R.string.birlik_pack_downloading));
                int message = TYPE_SHADER.equals(mProjectType) ? R.string.birlik_shader_installed : R.string.birlik_resourcepack_installed;
                Tools.runOnUiThread(() -> Toast.makeText(context, context.getString(message, file.getName()), Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    private static File getCurrentGameDir() {
        try {
            String key = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
            LauncherProfiles.load();
            MinecraftProfile profile = key == null ? null : LauncherProfiles.mainProfileJson.profiles.get(key);
            if (profile != null) return Tools.getGameDirPath(profile);
        } catch (RuntimeException ignored) {}
        return new File(Tools.DIR_GAME_NEW);
    }

    // ------------------------------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------------------------------

    private void loadIcon(ImageView view, @Nullable String url) {
        view.setTag(url);
        view.setImageDrawable(null);
        if (url == null) return;
        Bitmap cached = ICONS.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        PojavApplication.sExecutorService.execute(() -> {
            Bitmap bitmap = null;
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DownloadUtils.download(url, out);
                byte[] bytes = out.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 128);
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            } catch (Exception ignored) {}
            final Bitmap result = bitmap;
            if (result != null) ICONS.put(url, result);
            Tools.runOnUiThread(() -> { if (url.equals(view.getTag()) && result != null) view.setImageBitmap(result); });
        });
    }

    private class PackAdapter extends RecyclerView.Adapter<PackAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon; final TextView title, meta, description;
            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.birlik_pack_icon);
                title = v.findViewById(R.id.birlik_pack_title);
                meta = v.findViewById(R.id.birlik_pack_meta);
                description = v.findViewById(R.id.birlik_pack_description);
            }
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_birlik_pack, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            BirlikModrinth.Pack pack = mPacks.get(position);
            holder.title.setText(pack.title);
            holder.meta.setText(getString(R.string.birlik_packs_meta, pack.author, BirlikModrinth.formatDownloads(pack.downloads)));
            holder.description.setText(pack.description);
            loadIcon(holder.icon, pack.iconUrl);
            holder.itemView.setOnClickListener(v -> openDetail(pack));
        }

        @Override
        public int getItemCount() { return mPacks.size(); }
    }
}
