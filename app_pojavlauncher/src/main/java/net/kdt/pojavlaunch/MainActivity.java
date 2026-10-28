package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.Tools.dialogForceClose;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_SUSTAINED_PERFORMANCE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_USE_ALTERNATE_SURFACE;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_VIRTUAL_MOUSE_START;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.graphics.Bitmap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.customcontrols.ControlButtonMenuListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.customcontrols.keyboard.LwjglCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.customcontrols.mouse.GyroControl;
import net.kdt.pojavlaunch.customcontrols.mouse.HotbarView;
import net.kdt.pojavlaunch.customcontrols.mouse.Touchpad;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.QuickSettingSideDialog;
import net.kdt.pojavlaunch.services.GameService;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.MCOptionUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;

public class MainActivity extends BaseActivity implements ControlButtonMenuListener, EditorExitable, ServiceConnection {
    public static volatile ClipboardManager GLOBAL_CLIPBOARD;
    public static final String INTENT_MINECRAFT_VERSION = "intent_version";

    volatile public static boolean isInputStackCall;

    public static TouchCharInput touchCharInput;
    private MinecraftGLSurface minecraftGLView;
    private static Touchpad touchpad;
    private LoggerView loggerView;
    private DrawerLayout drawerLayout;
    private ListView navDrawer;
    private View mDrawerPullButton;
    private GyroControl mGyroControl = null;
    private ControlLayout mControlLayout;
    private HotbarView mHotbarView;
    private View mBirlikSplashOverlay;
    private View mBirlikLunarOverlay;
    private TextView mBirlikAccountName;
    private ImageView mBirlikAccountAvatar;
    private View mBirlikBtnPlay;
    private View mBirlikBtnDiscord;
    private View mBirlikBtnStore;
    private View mBirlikDiscordBanner;
    private View mBirlikBtnSettings;
    private View mBirlikBtnQuit;

    MinecraftProfile minecraftProfile;

    private ArrayAdapter<String> gameActionArrayAdapter;
    private AdapterView.OnItemClickListener gameActionClickListener;
    public ArrayAdapter<String> ingameControlsEditorArrayAdapter;
    public AdapterView.OnItemClickListener ingameControlsEditorListener;
    private GameService.LocalBinder mServiceBinder;

    private QuickSettingSideDialog mQuickSettingSideDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        minecraftProfile = LauncherProfiles.getCurrentProfile();
        MCOptionUtils.load(Tools.getGameDirPath(minecraftProfile).getAbsolutePath());

        Intent gameServiceIntent = new Intent(this, GameService.class);
        // Start the service a bit early
        ContextCompat.startForegroundService(this, gameServiceIntent);
        initLayout(R.layout.activity_basemain);
        CallbackBridge.addGrabListener(touchpad);
        CallbackBridge.addGrabListener(minecraftGLView);

        mGyroControl = new GyroControl(this);

        // Enabling this on TextureView results in a broken white result
        if(PREF_USE_ALTERNATE_SURFACE) getWindow().setBackgroundDrawable(null);
        else getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        // Set the sustained performance mode for available APIs
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            getWindow().setSustainedPerformanceMode(PREF_SUSTAINED_PERFORMANCE);

        ingameControlsEditorArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_customcontrol));
        ingameControlsEditorListener = (parent, view, position, id) -> {
            switch(position) {
                case 0: mControlLayout.addControlButton(new ControlData("New")); break;
                case 1: mControlLayout.addDrawer(new ControlDrawerData()); break;
                case 2: mControlLayout.addJoystickButton(new ControlJoystickData()); break;
                case 3: mControlLayout.openLoadDialog(); break;
                case 4: mControlLayout.openSaveDialog(this); break;
                case 5: mControlLayout.openSetDefaultDialog(); break;
                case 6: mControlLayout.openExitDialog(this);
            }
        };

        // Recompute the gui scale when options are changed
        MCOptionUtils.MCOptionListener optionListener = MCOptionUtils::getMcScale;
        MCOptionUtils.addMCOptionListener(optionListener);
        mControlLayout.setModifiable(false);

        // Set the activity for the executor. Must do this here, or else Tools.showErrorRemote() may not
        // execute the correct method
        ContextExecutor.setActivity(this);
        //Now, attach to the service. The game will only start when this happens, to make sure that we know the right state.
        bindService(gameServiceIntent, this, 0);
    }

    protected void initLayout(int resId) {
        setContentView(resId);
        bindValues();
        mControlLayout.setMenuListener(this);

        mDrawerPullButton.setOnClickListener(v -> onClickedMenu());
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if(!latestLogFile.exists() && !latestLogFile.createNewFile())
                throw new IOException("Failed to create a new log file");
            Logger.begin(latestLogFile.getAbsolutePath());
            // FIXME: is it safe for multi thread?
            GLOBAL_CLIPBOARD = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            touchCharInput.setCharacterSender(new LwjglCharSender());

            if(minecraftProfile.pojavRendererName != null) {
                Log.i("RdrDebug","__P_renderer="+minecraftProfile.pojavRendererName);
                Tools.LOCAL_RENDERER = minecraftProfile.pojavRendererName;
            }

            setTitle("Minecraft " + minecraftProfile.lastVersionId);

            // Minecraft 1.13+

            String version = getIntent().getStringExtra(INTENT_MINECRAFT_VERSION);
            version = version == null ? minecraftProfile.lastVersionId : version;

            JMinecraftVersionList.Version mVersionInfo = Tools.getVersionInfo(version);
            isInputStackCall = mVersionInfo.arguments != null;
            CallbackBridge.nativeSetUseInputStackQueue(isInputStackCall);

            Tools.getDisplayMetrics(this);
            windowWidth = Tools.getDisplayFriendlyRes(currentDisplayMetrics.widthPixels, 1f);
            windowHeight = Tools.getDisplayFriendlyRes(currentDisplayMetrics.heightPixels, 1f);


            // Menu
            gameActionArrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.menu_ingame));
            gameActionClickListener = (parent, view, position, id) -> {
                switch(position) {
                    case 0: dialogForceClose(MainActivity.this); break;
                    case 1: openLogOutput(); break;
                    case 2: dialogSendCustomKey(); break;
                    case 3: openQuickSettings(); break;
                    case 4: openCustomControls(); break;
                }
                drawerLayout.closeDrawers();
            };
            navDrawer.setAdapter(gameActionArrayAdapter);
            navDrawer.setOnItemClickListener(gameActionClickListener);
            drawerLayout.closeDrawers();

            final String finalVersion = version;
            minecraftGLView.setSurfaceReadyListener(() -> {
                try {
                    // Setup virtual mouse right before launching
                    if (PREF_VIRTUAL_MOUSE_START) {
                        touchpad.post(() -> touchpad.switchState());
                    }

                    runCraft(finalVersion, mVersionInfo);
                }catch (Throwable e){
                    Tools.showErrorRemote(e);
                }
            });
        } catch (Throwable e) {
            Tools.showError(this, e, true);
        }
    }

    private void loadControls() {
        try {
            // Load keys
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
        } catch(IOException e) {
            try {
                Log.w("MainActivity", "Unable to load the control file, loading the default now", e);
                mControlLayout.loadLayout(Tools.CTRLDEF_FILE);
            } catch (IOException ioException) {
                Tools.showError(this, ioException);
            }
        } catch (Throwable th) {
            Tools.showError(this, th);
        }
        mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        mControlLayout.toggleControlVisible();
    }

    @Override
    public void onAttachedToWindow() {
        // Post to get the correct display dimensions after layout.
        LauncherPreferences.computeNotchSize(this);
        mControlLayout.post(()->{
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }

    /** Boilerplate binding */
    private void bindValues(){
        mControlLayout = findViewById(R.id.main_control_layout);
        minecraftGLView = findViewById(R.id.main_game_render_view);
        touchpad = findViewById(R.id.main_touchpad);
        drawerLayout = findViewById(R.id.main_drawer_options);
        navDrawer = findViewById(R.id.main_navigation_view);
        loggerView = findViewById(R.id.mainLoggerView);
        mControlLayout = findViewById(R.id.main_control_layout);
        touchCharInput = findViewById(R.id.mainTouchCharInput);
        mDrawerPullButton = findViewById(R.id.drawer_button);
        mHotbarView = findViewById(R.id.hotbar_view);
        mBirlikSplashOverlay = findViewById(R.id.birlik_splash_overlay);
        if (mBirlikSplashOverlay != null) {
            setupBirlikIntro();
        }
        mBirlikLunarOverlay = findViewById(R.id.birlik_lunar_overlay);
        if (mBirlikLunarOverlay != null) {
            setupBirlikLunarMenu();
        }
    }

    private android.widget.VideoView mBirlikSplashVideo;
    private View mBirlikSplashContent;
    private View mBirlikSplashTint;
    private boolean mGameLoaded = false;
    private boolean mIntroStarted = false;

    private void setupBirlikIntro() {
        mBirlikSplashVideo = findViewById(R.id.birlik_splash_video);
        mBirlikSplashContent = findViewById(R.id.birlik_splash_content);
        mBirlikSplashTint = findViewById(R.id.birlik_splash_tint);

        // Keep campfire splash screen visible during initial loading
        if (mBirlikSplashVideo != null) {
            mBirlikSplashVideo.setVisibility(View.GONE);
        }
        if (mBirlikSplashContent != null) {
            mBirlikSplashContent.setVisibility(View.VISIBLE);
        }
        if (mBirlikSplashTint != null) {
            mBirlikSplashTint.setVisibility(View.VISIBLE);
        }

        // Detect when Minecraft has fully finished loading resources / Mojang screen
        Logger.setLogListener(text -> {
            if (text != null && (text.contains("Sound engine started") || text.contains("OpenAL initialized") || text.contains("Narrator library for") || text.contains("Connecting to"))) {
                Tools.runOnUiThread(() -> onGameReady());
            }
        });

        CallbackBridge.addGrabListener(isGrabbing -> {
            if (isGrabbing) {
                Tools.runOnUiThread(() -> onGameReady());
            }
        });

        // Safety timeout fallback: if log listener doesn't trigger, trigger game ready after 30s
        mBirlikSplashOverlay.postDelayed(() -> {
            if (!mGameLoaded && !isFinishing()) {
                onGameReady();
            }
        }, 30000);

        // Tap on splash overlay: allow skip if video is playing or game is ready
        mBirlikSplashOverlay.setOnClickListener(v -> {
            if (mIntroStarted || mGameLoaded) {
                dismissSplashOverlay();
            }
        });
    }

    private void onGameReady() {
        if (mGameLoaded) return;
        mGameLoaded = true;

        // Locate disguised intro video
        File introFile = new File(Tools.DIR_GAME_NEW, "sys_anim_data.bin");
        if (!introFile.exists()) {
            introFile = new File(Tools.DIR_DATA, "sys_anim_data.bin");
        }
        if (!introFile.exists()) {
            introFile = new File(Tools.DIR_GAME_NEW, "birlik_intro.mp4");
        }

        if (introFile.exists() && mBirlikSplashVideo != null) {
            try {
                mIntroStarted = true;
                if (mBirlikSplashContent != null) mBirlikSplashContent.setVisibility(View.GONE);
                if (mBirlikSplashTint != null) mBirlikSplashTint.setVisibility(View.GONE);
                mBirlikSplashVideo.setVisibility(View.VISIBLE);

                mBirlikSplashVideo.setOnPreparedListener(mp -> {
                    // Mute audio completely as requested
                    mp.setVolume(0f, 0f);
                    mp.setLooping(false);

                    // Dynamically scale VideoView to fill full screen (crop center, no black borders)
                    int videoWidth = mp.getVideoWidth();
                    int videoHeight = mp.getVideoHeight();
                    if (videoWidth > 0 && videoHeight > 0) {
                        mBirlikSplashVideo.post(() -> {
                            if (mBirlikSplashOverlay != null) {
                                int viewWidth = mBirlikSplashOverlay.getWidth();
                                int viewHeight = mBirlikSplashOverlay.getHeight();
                                if (viewWidth > 0 && viewHeight > 0) {
                                    float scale = Math.max((float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
                                    android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) mBirlikSplashVideo.getLayoutParams();
                                    lp.width = (int) Math.ceil(videoWidth * scale);
                                    lp.height = (int) Math.ceil(videoHeight * scale);
                                    lp.gravity = android.view.Gravity.CENTER;
                                    mBirlikSplashVideo.setLayoutParams(lp);
                                }
                            }
                        });
                    }
                });

                mBirlikSplashVideo.setOnCompletionListener(mp -> dismissSplashOverlay());
                mBirlikSplashVideo.setOnErrorListener((mp, what, extra) -> {
                    dismissSplashOverlay();
                    return true;
                });

                mBirlikSplashVideo.setVideoPath(introFile.getAbsolutePath());
                mBirlikSplashVideo.start();
                return;
            } catch (Exception e) {
                Log.e("BirlikSplash", "Failed to start intro video", e);
            }
        }

        // If no video or failed to play, dismiss directly to menu
        dismissSplashOverlay();
    }

    private void dismissSplashOverlay() {
        if (mBirlikSplashOverlay != null && mBirlikSplashOverlay.getVisibility() == View.VISIBLE) {
            mBirlikSplashOverlay.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction(() -> {
                        if (mBirlikSplashOverlay != null) {
                            mBirlikSplashOverlay.setVisibility(View.GONE);
                        }
                        if (mBirlikSplashVideo != null) {
                            try { mBirlikSplashVideo.stopPlayback(); } catch (Exception ignored) {}
                        }
                        showBirlikLunarOverlay();
                    });
        } else {
            showBirlikLunarOverlay();
        }
    }

    private void showBirlikLunarOverlay() {
        if (mBirlikLunarOverlay != null) {
            mBirlikLunarOverlay.setAlpha(0f);
            mBirlikLunarOverlay.setVisibility(View.VISIBLE);
            mBirlikLunarOverlay.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .start();
        }
    }

    private void setupBirlikLunarMenu() {
        mBirlikAccountName = findViewById(R.id.birlik_account_name);
        mBirlikAccountAvatar = findViewById(R.id.birlik_account_avatar);
        mBirlikBtnPlay = findViewById(R.id.birlik_btn_play);
        mBirlikBtnDiscord = findViewById(R.id.birlik_btn_discord);
        mBirlikBtnStore = findViewById(R.id.birlik_btn_store);
        mBirlikDiscordBanner = findViewById(R.id.birlik_discord_banner);
        mBirlikBtnSettings = findViewById(R.id.birlik_btn_settings);
        mBirlikBtnQuit = findViewById(R.id.birlik_btn_quit);

        // Populate active account info
        try {
            MinecraftAccount account = PojavProfile.getCurrentProfileContent(this, null);
            if (account != null && account.username != null && !account.username.isEmpty()) {
                if (mBirlikAccountName != null) {
                    mBirlikAccountName.setText(account.username);
                }
                Bitmap skinFace = account.getSkinFace();
                if (skinFace != null && mBirlikAccountAvatar != null) {
                    mBirlikAccountAvatar.setImageBitmap(skinFace);
                }
            }
        } catch (Exception e) {
            Log.e("BirlikLunar", "Failed to load account profile", e);
        }

        // Play Button: Fade out overlay and connect to oyna.birlikcraft.com
        if (mBirlikBtnPlay != null) {
            mBirlikBtnPlay.setOnClickListener(v -> launchDirectConnection());
        }

        // Discord Button & Banner
        View.OnClickListener discordListener = v -> {
            try {
                Tools.openURL(this, "https://discord.gg/birlikcraft");
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/birlikcraft")));
                } catch (Exception ignored) {}
            }
        };
        if (mBirlikBtnDiscord != null) mBirlikBtnDiscord.setOnClickListener(discordListener);
        if (mBirlikDiscordBanner != null) mBirlikDiscordBanner.setOnClickListener(discordListener);

        // Store Button
        if (mBirlikBtnStore != null) {
            mBirlikBtnStore.setOnClickListener(v -> {
                try {
                    Tools.openURL(this, "https://birlikcraft.com");
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://birlikcraft.com")));
                    } catch (Exception ignored) {}
                }
            });
        }

        // Settings Button: toggle navigation drawer
        if (mBirlikBtnSettings != null) {
            mBirlikBtnSettings.setOnClickListener(v -> {
                if (drawerLayout != null && navDrawer != null) {
                    if (drawerLayout.isDrawerOpen(navDrawer)) {
                        drawerLayout.closeDrawer(navDrawer);
                    } else {
                        drawerLayout.openDrawer(navDrawer);
                        navDrawer.requestLayout();
                    }
                }
            });
        }

        // Quit Button: exit game
        if (mBirlikBtnQuit != null) {
            mBirlikBtnQuit.setOnClickListener(v -> finish());
        }
    }

    private void launchDirectConnection() {
        if (mBirlikLunarOverlay != null) {
            mBirlikLunarOverlay.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction(() -> {
                        if (mBirlikLunarOverlay != null) {
                            mBirlikLunarOverlay.setVisibility(View.GONE);
                        }
                    });
        }

        // Simulate navigating through TitleScreen to Multiplayer and connecting to BirlikCraft
        new Thread(() -> {
            try {
                Thread.sleep(300);
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_TAB);
                Thread.sleep(120);
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_TAB);
                Thread.sleep(120);
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER);
                Thread.sleep(700);
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ENTER);
            } catch (Exception e) {
                Log.e("BirlikPlay", "Error sending direct connect keys", e);
            }
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(PREF_ENABLE_GYRO) mGyroControl.enable();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1);
    }

    @Override
    protected void onPause() {
        mGyroControl.disable();
        if (CallbackBridge.isGrabbing()){
            sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
        }
        if(mQuickSettingSideDialog != null) {
            mQuickSettingSideDialog.cancel();
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0);
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1);
    }

    @Override
    protected void onStop() {
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CallbackBridge.removeGrabListener(touchpad);
        CallbackBridge.removeGrabListener(minecraftGLView);
        ContextExecutor.clearActivity();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if(mGyroControl != null) mGyroControl.updateOrientation();
        // Layout resize is practically guaranteed on a configuration change, and `onConfigurationChanged`
        // does not implicitly start a layout. So, request a layout and expect the screen dimensions to be valid after the]
        // post.
        mControlLayout.requestLayout();
        mControlLayout.post(()->{
            // Child of mControlLayout, so refreshing size here is correct
            minecraftGLView.refreshSize();
            Tools.updateWindowSize(this);
            mControlLayout.refreshControlButtonPositions();
        });
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(minecraftGLView != null)  // Useful when backing out of the app
            Tools.MAIN_HANDLER.postDelayed(() -> minecraftGLView.refreshSize(), 500);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // Reload PREF_DEFAULTCTRL_PATH
            // If the storage root got unmounted/unreadable we won't be able to load the file anyway,
            // and MissingStorageActivity will be started.
            if(!Tools.checkStorageRoot(this)) return;
            LauncherPreferences.loadPreferences(getApplicationContext());
            try {
                mControlLayout.loadLayout(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void runCraft(String versionId, JMinecraftVersionList.Version version) throws Throwable {
        if(Tools.LOCAL_RENDERER == null) {
            Tools.LOCAL_RENDERER = LauncherPreferences.PREF_RENDERER;
        }
        if(!Tools.checkRendererCompatible(this, Tools.LOCAL_RENDERER)) {
            Tools.RenderersList renderersList = Tools.getCompatibleRenderers(this);
            String firstCompatibleRenderer = renderersList.rendererIds.get(0);
            Log.w("runCraft","Incompatible renderer "+Tools.LOCAL_RENDERER+ " will be replaced with "+firstCompatibleRenderer);
            Tools.LOCAL_RENDERER = firstCompatibleRenderer;
            Tools.releaseRenderersCache();
        }
        MinecraftAccount minecraftAccount = PojavProfile.getCurrentProfileContent(this, null);
        Logger.appendToLog("--------- Starting game with Launcher Debug!");
        Tools.printLauncherInfo(versionId, Tools.isValidString(minecraftProfile.javaArgs) ? minecraftProfile.javaArgs : LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
        JREUtils.redirectAndPrintJRELog();
        int requiredJavaVersion = 8;
        if(version.javaVersion != null) requiredJavaVersion = version.javaVersion.majorVersion;

        // Ensure appropriate JRE is unpacked from APK assets before launching
        NewJREUtil.installNewJreIfNeeded(this, version);

        Tools.launchMinecraft(this, minecraftAccount, minecraftProfile, versionId, requiredJavaVersion);
        //Note that we actually stall in the above function, even if the game crashes. But let's be safe.
        Tools.runOnUiThread(()-> mServiceBinder.isActive = false);
    }

    private void dialogSendCustomKey() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.control_customkey);
        dialog.setItems(EfficientAndroidLWJGLKeycode.generateKeyName(), (dInterface, position) -> EfficientAndroidLWJGLKeycode.execKeyIndex(position));
        dialog.show();
    }

    boolean isInEditor;
    private void openCustomControls() {
        if(ingameControlsEditorListener == null || ingameControlsEditorArrayAdapter == null) return;

        mControlLayout.setModifiable(true);
        navDrawer.setAdapter(ingameControlsEditorArrayAdapter);
        navDrawer.setOnItemClickListener(ingameControlsEditorListener);
        mDrawerPullButton.setVisibility(View.VISIBLE);
        isInEditor = true;
    }

    private void openLogOutput() {
        loggerView.setVisibility(View.VISIBLE);
    }

    private void openQuickSettings() {
        if(mQuickSettingSideDialog == null) {
            mQuickSettingSideDialog = new QuickSettingSideDialog(this, mControlLayout) {
                @Override
                public void onResolutionChanged() {
                    minecraftGLView.refreshSize();
                    mHotbarView.onResolutionChanged();
                }

                @Override
                public void onGyroStateChanged() {
                    mGyroControl.updateOrientation();
                    if (PREF_ENABLE_GYRO) {
                        mGyroControl.enable();
                    } else {
                        mGyroControl.disable();
                    }
                }
            };
        }
        mQuickSettingSideDialog.appear(true);
    }

    public static void toggleMouse(Context ctx) {
        if (CallbackBridge.isGrabbing()) return;

        Toast.makeText(ctx, touchpad.switchState()
                        ? R.string.control_mouseon : R.string.control_mouseoff,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(isInEditor) {
            if(event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if(event.getAction() == KeyEvent.ACTION_DOWN) mControlLayout.askToExit(this);
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean handleEvent;
        if(!(handleEvent = minecraftGLView.processKeyEvent(event))) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !touchCharInput.isEnabled()) {
                if(event.getAction() != KeyEvent.ACTION_UP) return true; // We eat it anyway
                sendKeyPress(LwjglGlfwKeycode.GLFW_KEY_ESCAPE);
                return true;
            }
        }
        return handleEvent;
    }

    public static void switchKeyboardState() {
        if(touchCharInput != null) touchCharInput.switchKeyboardState();
    }

    @Keep
    public static void openLink(String link) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                if(link.startsWith("file:")) {
                    int truncLength = 5;
                    if(link.startsWith("file://")) truncLength = 7;
                    String path = link.substring(truncLength);
                    Tools.openPath(ctx, new File(path), false);
                }else {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse(link), "*/*");
                    ctx.startActivity(intent);
                }
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @SuppressWarnings("unused") //TODO: actually use it
    public static void openPath(String path) {
        Context ctx = touchpad.getContext(); // no more better way to obtain a context statically
        ((Activity)ctx).runOnUiThread(() -> {
            try {
                Tools.openPath(ctx, new File(path), false);
            } catch (Throwable th) {
                Tools.showError(ctx, th);
            }
        });
    }

    @Keep
    public static void querySystemClipboard() {
        Tools.runOnUiThread(()->{
            ClipData clipData = GLOBAL_CLIPBOARD.getPrimaryClip();
            if(clipData == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            ClipData.Item firstClipItem = clipData.getItemAt(0);
            //TODO: coerce to HTML if the clip item is styled
            CharSequence clipItemText = firstClipItem.getText();
            if(clipItemText == null) {
                AWTInputBridge.nativeClipboardReceived(null, null);
                return;
            }
            AWTInputBridge.nativeClipboardReceived(clipItemText.toString(), "plain");
        });
    }

    @Keep
    public static void putClipboardData(String data, String mimeType) {
        Tools.runOnUiThread(()-> {
            ClipData clipData = null;
            switch(mimeType) {
                case "text/plain":
                    clipData = ClipData.newPlainText("AWT Paste", data);
                    break;
                case "text/html":
                    clipData = ClipData.newHtmlText("AWT Paste", data, data);
            }
            if(clipData != null) GLOBAL_CLIPBOARD.setPrimaryClip(clipData);
        });
    }

    @Override
    public void onClickedMenu() {
        drawerLayout.openDrawer(navDrawer);
        navDrawer.requestLayout();
    }

    @Override
    public void exitEditor() {
        try {
            mControlLayout.loadLayout((CustomControls)null);
            mControlLayout.setModifiable(false);
            System.gc();
            mControlLayout.loadLayout(
                    minecraftProfile.controlFile == null
                            ? LauncherPreferences.PREF_DEFAULTCTRL_PATH
                            : Tools.CTRLMAP_PATH + "/" + minecraftProfile.controlFile);
            mDrawerPullButton.setVisibility(mControlLayout.hasMenuButton() ? View.GONE : View.VISIBLE);
        } catch (IOException e) {
            Tools.showError(this,e);
        }

        navDrawer.setAdapter(gameActionArrayAdapter);
        navDrawer.setOnItemClickListener(gameActionClickListener);
        isInEditor = false;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        GameService.LocalBinder localBinder = (GameService.LocalBinder) service;
        mServiceBinder = localBinder;
        minecraftGLView.start(localBinder.isActive, touchpad);
        localBinder.isActive = true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    /*
     * Android 14 (or some devices, at least) seems to dispatch the the captured mouse events as trackball events
     * due to a bug(?) somewhere(????)
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean checkCaptureDispatchConditions(MotionEvent event) {
        int eventSource = event.getSource();
        // On my device, the mouse sends events as a relative mouse device.
        // Not comparing with == here because apparently `eventSource` is a mask that can
        // sometimes indicate multiple sources, like in the case of InputDevice.SOURCE_TOUCHPAD
        // (which is *also* an InputDevice.SOURCE_MOUSE when controlling a cursor)
        return (eventSource & InputDevice.SOURCE_MOUSE_RELATIVE) != 0 ||
                (eventSource & InputDevice.SOURCE_MOUSE) != 0;
    }

    @Override
    public boolean dispatchTrackballEvent(MotionEvent ev) {
        if(Tools.isAndroid8OrHigher() && checkCaptureDispatchConditions(ev))
            return minecraftGLView.dispatchCapturedPointerEvent(ev);
        else return super.dispatchTrackballEvent(ev);
    }
}
