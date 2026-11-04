package com.minimaui.app.ui.home

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.minimaui.app.ui.components.BootLogo
import com.minimaui.app.ui.components.BootVideo
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.minimaui.app.ui.favorites.Direction
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.InputDevice
import android.view.MotionEvent
import com.minimaui.app.audio.Haptics
import kotlin.math.abs
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.minimaui.app.audio.Sfx
import com.minimaui.app.audio.UiSounds
import com.minimaui.app.model.AppLibrary
import com.minimaui.app.model.Library
import com.minimaui.app.model.Settings
import com.minimaui.app.ui.allgames.AllGamesScreen
import com.minimaui.app.ui.allgames.FocusCenterY
import com.minimaui.app.ui.allgames.ListCenterX
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import com.minimaui.app.ui.favorites.BentoLayouts
import com.minimaui.app.ui.apps.AppsScreen
import com.minimaui.app.ui.favorites.BentoScreen
import com.minimaui.app.ui.menu.FavoritesMoreMenu
import com.minimaui.app.ui.menu.FolderChooser
import com.minimaui.app.ui.menu.GameDetailsDialog
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.minimaui.app.ui.components.BlurFadeSwitch
import com.minimaui.app.ui.components.LaunchZoom
import com.minimaui.app.ui.components.SwitchMotion
import com.minimaui.app.ui.components.ControlHintBar
import com.minimaui.app.ui.components.EaseInOut
import com.minimaui.app.ui.components.centerBlur
import com.minimaui.app.ui.components.progressiveBlur
import com.minimaui.app.ui.components.OverlayMotion
import com.minimaui.app.ui.components.LocalFocusPulse
import com.minimaui.app.ui.components.WaveOff
import com.minimaui.app.ui.components.WavePulses
import com.minimaui.app.ui.components.WaveButtonRipples
import com.minimaui.app.ui.components.WaveRibbon
import com.minimaui.app.ui.components.XmbWave
import com.minimaui.app.ui.components.ProvideFocusPulse
import com.minimaui.app.ui.components.LevelBackdrop
import com.minimaui.app.ui.components.LevelCenterX
import com.minimaui.app.ui.components.LevelCenterY
import com.minimaui.app.ui.components.LevelBlurRadius
import com.minimaui.app.ui.components.LevelHeightShare
import com.minimaui.app.ui.components.LevelSlider
import com.minimaui.app.ui.components.AmbientClock
import com.minimaui.app.ui.components.ToastPill
import com.minimaui.app.ui.components.withHalfRateFrames
import com.minimaui.app.ui.components.spotBlur
import androidx.compose.runtime.mutableFloatStateOf
import com.minimaui.app.ui.components.StatusPill
import com.minimaui.app.ui.menu.AppPicker
import com.minimaui.app.ui.menu.MenuOverlay
import com.minimaui.app.ui.menu.SearchDialog
import com.minimaui.app.ui.menu.TitleKeyboardDialog
import com.minimaui.app.ui.menu.TitleEditDialog
import com.minimaui.app.ui.theme.Dimens
import com.minimaui.app.ui.theme.OledBlack

private enum class HomePage { Favorites, AllGames, Apps }

/** The edge sound (pushing against the end of a page, list or menu) is off for now; switch it on here. */
private const val EdgeSoundOn = false

/** The user's boot animation video: playing, played (or skipped) and fading out, failed to play (the logo comes instead), or not (or no longer) there. */
private enum class BootVideoPhase { Playing, Finished, Failed, Gone }

private val PillCenterX = (Dimens.DesignWidth - Dimens.StatusPillWidth.value) / 2f
// In the corner the pill's left edge sits here and its right end runs past the screen edge (like the PS3's).
private const val PillCornerX = 1465f

@Composable
fun HomeRoot(
    library: Library,
    appLibrary: AppLibrary,
    settings: Settings,
    actions: HomeActions,
    uiSounds: UiSounds,
    launching: Boolean,
    playBoot: Boolean,
    dimmed: Boolean = false,
    idle: Boolean = false,
    onBootPlayed: () -> Unit,
    onBootVideoPlaying: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state = remember(library, appLibrary, settings) { HomeState(library, appLibrary, settings, actions) }
    val focusRequester = remember { FocusRequester() }
    // The boot sequence (logo centred, then gliding down to Favorites); 1 = finished or skipped.
    val boot = remember { Animatable(if (playBoot) 0f else 1f) }
    val bootAppear = remember { Animatable(if (playBoot) 0f else 1f) }
    // The ambient clock fades in over the dimmed screen with the boot logo's dim patch, and blurs the middle of the screen under it.
    val ambient = remember { Animatable(0f) }
    LaunchedEffect(dimmed) {
        withHalfRateFrames { ambient.animateTo(if (dimmed) 1f else 0f, tween(if (dimmed) 700 else 150)) }
    }
    // A boot animation of the user's own (a video) plays instead of the logo; the video is the one set when MinimaUI started.
    val bootVideo = remember { if (playBoot) settings.bootVideoUri else null }
    var videoPhase by remember { mutableStateOf(if (bootVideo != null) BootVideoPhase.Playing else BootVideoPhase.Gone) }
    val videoAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (playBoot) {
            onBootPlayed()
            if (bootVideo != null) {
                onBootVideoPlaying(true)
                snapshotFlow { videoPhase }.first { it != BootVideoPhase.Playing }
                onBootVideoPlaying(false)
                if (videoPhase == BootVideoPhase.Finished) {
                    // The video fades out over the screen, which settles as it does after the logo.
                    coroutineScope {
                        launch { videoAlpha.animateTo(0f, tween(600)) }
                        boot.animateTo(1f, tween(1000, easing = EaseInOut))
                    }
                    videoPhase = BootVideoPhase.Gone
                    return@LaunchedEffect
                }
                videoPhase = BootVideoPhase.Gone
            }
            bootAppear.animateTo(1f, tween(450))
            delay(900)
            boot.animateTo(1f, tween(1000, easing = EaseInOut))
        }
    }
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember { Haptics(context) }
    state.haptic = haptics::tick
    state.pageSound = { if (settings.menuSounds) uiSounds.play(Sfx.PageSwitch) }
    state.confirmSound = { if (settings.menuSounds) uiSounds.play(Sfx.CancelConfirm) }
    state.menuSound = { if (settings.menuSounds) uiSounds.play(Sfx.Menu) }
    state.editModeSound = { if (settings.menuSounds) uiSounds.play(Sfx.EditMode) }
    state.searchSound = { if (settings.menuSounds) uiSounds.play(Sfx.ModeSwitch) }
    state.keySound = { if (settings.menuSounds) uiSounds.play(Sfx.Keyboard) }
    state.switchSound = { if (settings.menuSounds) uiSounds.play(Sfx.ModeSwitch) }
    state.toastSound = { if (settings.menuSounds) uiSounds.play(Sfx.Toast) }

    // The right stick turns pages: each push past the threshold counts once, and it must return to centre to count again.
    DisposableEffect(view, state) {
        var held: Direction? = null
        view.setOnGenericMotionListener { _, event ->
            if (event.source and InputDevice.SOURCE_JOYSTICK == 0 || event.actionMasked != MotionEvent.ACTION_MOVE) return@setOnGenericMotionListener false
            val x = if (abs(event.getAxisValue(MotionEvent.AXIS_Z)) >= abs(event.getAxisValue(MotionEvent.AXIS_RX))) event.getAxisValue(MotionEvent.AXIS_Z) else event.getAxisValue(MotionEvent.AXIS_RX)
            val y = if (abs(event.getAxisValue(MotionEvent.AXIS_RZ)) >= abs(event.getAxisValue(MotionEvent.AXIS_RY))) event.getAxisValue(MotionEvent.AXIS_RZ) else event.getAxisValue(MotionEvent.AXIS_RY)
            val pushed = when {
                abs(y) > abs(x) && abs(y) > 0.6f -> if (y < 0) Direction.Up else Direction.Down
                abs(x) >= abs(y) && abs(x) > 0.6f -> if (x < 0) Direction.Left else Direction.Right
                else -> null
            }
            if (pushed == null) {
                if (abs(x) < 0.3f && abs(y) < 0.3f) held = null
            } else if (pushed != held) {
                held = pushed
                state.onRightStick(pushed)
            }
            false
        }
        onDispose { view.setOnGenericMotionListener(null) }
    }

    // Leaving the app (a game, another window) can swallow Select's release: forget that it was held.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                state.releaseSelect()
                state.releaseStart()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reclaim key focus at launch and whenever the title editor (which owns focus) closes.
    LaunchedEffect(state.textInputActive) {
        if (!state.textInputActive) focusRequester.requestFocus()
    }
    // L2 and R2 held together open the search.
    val triggers = remember { BooleanArray(2) }
    // The shoulder buttons send ripples along the Minima ribbon.
    val pulses = remember { WavePulses() }
    // Moving between All Games and Apps sends a ripple in from the side the other screen lies on (the content slides that way too).
    var lastScreen by remember { mutableStateOf(state.screen) }
    LaunchedEffect(state.screen) {
        val from = lastScreen
        lastScreen = state.screen
        if (settings.waveStyle == WaveRibbon && from != HomeScreen.Favorites && state.screen != HomeScreen.Favorites && from != state.screen) {
            pulses.fire(fromRight = state.screen == HomeScreen.Apps, strength = 1.3f)
        }
    }
    BackHandler(enabled = true) { if (state.canStepBack()) state.back() }
    BackHandler(enabled = state.overlayOpen || state.editMode || state.favView != FavView.Favorites) { state.back() }

    val overlayOpenForBlur = state.overlayOpen
    val backdropBlur by animateDpAsState(
        targetValue = if (state.overlayOpen) 28.dp else 0.dp,
        animationSpec = tween(200),
        label = "overlayBackdropBlur"
    )

    // Launching a game: the screen blurs and fades to black first, so the other app appears without a slide.
    val launchFade by animateFloatAsState(
        targetValue = if (launching) 1f else 0f,
        animationSpec = tween(if (launching) 200 else 340, easing = EaseInOut),
        label = "launchFade"
    )

    // The volume / brightness bars: quick in, slow out; they read the system's current level while they are up.
    val levelAlpha by animateFloatAsState(
        targetValue = if (state.levelVisible) 1f else 0f,
        animationSpec = tween(if (state.levelVisible) 500 else 1000),
        label = "levelAlpha"
    )
    // The bar leaves a moment after the last change, whether or not Select is still (or seems still) held, so it can't stick.
    LaunchedEffect(state.levelStamp) {
        if (state.levelVisible) {
            delay(900)
            state.hideLevel()
        }
    }
    // Holding Start for half a second opens Android's settings.
    LaunchedEffect(state.startHeld) {
        if (state.startHeld) {
            delay(500)
            state.fireStartHold()
        }
    }
    // A Select whose release never arrived is forgotten after a few seconds.
    LaunchedEffect(state.selectHeld) {
        if (state.selectHeld) {
            delay(4000)
            state.releaseSelect()
        }
    }
    val audioManager = remember { context.getSystemService(android.media.AudioManager::class.java) }
    var levelFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.levelVisible, state.levelKind) {
        while (state.levelVisible) {
            levelFraction = if (state.levelKind == LevelKind.Volume) {
                audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) / audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
            } else {
                android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            }
            delay(80)
        }
    }
    // The system changes the levels itself (hardware keys, the Select hotkeys): show the bar when it does.
    DisposableEffect(context, state) {
        val volumeReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == android.media.AudioManager.STREAM_MUSIC) {
                    state.onSystemLevel(LevelKind.Volume)
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, volumeReceiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        fun brightness() = android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, -1)
        // With manual brightness any change is the user's; auto-brightness also drifts on its own, so it shows no bar.
        fun autoBrightness() = android.provider.Settings.System.getInt(
            context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE, 0
        ) == android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        var lastBrightness = brightness()
        fun checkBrightness() {
            val now = brightness()
            if (now != lastBrightness) {
                lastBrightness = now
                if (!autoBrightness()) state.onSystemLevel(LevelKind.Brightness)
            }
        }
        val brightnessObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = checkBrightness()
        }
        context.contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, brightnessObserver)
        onDispose {
            context.unregisterReceiver(volumeReceiver)
            context.contentResolver.unregisterContentObserver(brightnessObserver)
        }
    }

    // A short message (the sort mode after Y in the All Games Edit Mode).
    var toastShown by remember { mutableStateOf(false) }
    val toastAlpha by animateFloatAsState(
        targetValue = if (toastShown) 1f else 0f,
        animationSpec = tween(if (toastShown) 200 else 1000),
        label = "toastAlpha"
    )
    // A manual rescan reports what it found.
    LaunchedEffect(library.rescanStamp) {
        if (library.rescanStamp > 0) state.onRescanResult(library.rescanAdded)
    }
    LaunchedEffect(state.toastStamp) {
        if (state.toastStamp > 0) {
            toastShown = true
            delay(1400)
            toastShown = false
        }
    }

    ProvideFocusPulse(dimmed, idle) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OledBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // The boot video: a button press skips it (and is not passed on).
                if (videoPhase == BootVideoPhase.Playing) {
                    if (event.type == KeyEventType.KeyDown) videoPhase = BootVideoPhase.Finished
                    return@onPreviewKeyEvent true
                }
                // Select is a tap (details card) or a hold with a shoulder button (volume / brightness bars).
                if (event.key == Key.ButtonStart) {
                    val menuWas = state.menuOpen
                    val toggled = state.onStart(event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount > 0)
                    // Start opens (and closes) the settings menu with the menu sound; the other things it confirms keep the old one.
                    if (toggled && settings.menuSounds) uiSounds.play(if (state.menuOpen != menuWas) Sfx.Menu else Sfx.MenuNavigate)
                    return@onPreviewKeyEvent true
                }
                if (event.key == Key.ButtonSelect) {
                    val opened = state.onSelect(event.type == KeyEventType.KeyDown, event.nativeKeyEvent.repeatCount > 0)
                    if (opened && settings.menuSounds) uiSounds.play(Sfx.Navigate)
                    return@onPreviewKeyEvent true
                }
                if (state.selectHeld && (event.key == Key.ButtonL1 || event.key == Key.ButtonR1 || event.key == Key.ButtonL2 || event.key == Key.ButtonR2)) {
                    if (event.type == KeyEventType.KeyDown) state.onSelectCombo()
                    return@onPreviewKeyEvent true
                }
                // Any other button means Select is not being held for a combo (guards against a lost release).
                if (state.selectHeld && event.type == KeyEventType.KeyDown) state.releaseSelect()
                // A ripple enters from the side of the shoulder button pressed (the triggers send a bigger one), where the ribbon is shown.
                if (WaveButtonRipples && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && settings.waveStyle == WaveRibbon &&
                    state.screen != HomeScreen.Favorites && !state.overlayOpen
                ) {
                    when (event.key) {
                        Key.ButtonL1 -> pulses.fire(fromRight = false)
                        Key.ButtonR1 -> pulses.fire(fromRight = true)
                        Key.ButtonL2 -> pulses.fire(fromRight = false, strength = 1.5f)
                        Key.ButtonR2 -> pulses.fire(fromRight = true, strength = 1.5f)
                    }
                }
                // The RP6's back buttons (M1 is BUTTON_C, key code 98, on the left; M2 is BUTTON_Z, 101, on the right) send the same ripple as switching between All Games and Apps.
                if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && settings.waveStyle == WaveRibbon &&
                    state.screen != HomeScreen.Favorites && !state.overlayOpen
                ) {
                    when (event.key) {
                        Key.ButtonC -> pulses.fire(fromRight = false, strength = 1.3f)
                        Key.ButtonZ -> pulses.fire(fromRight = true, strength = 1.3f)
                    }
                }
                val trigger = when (event.key) {
                    Key.ButtonL2 -> 0
                    Key.ButtonR2 -> 1
                    else -> -1
                }
                if (trigger >= 0) {
                    triggers[trigger] = event.type == KeyEventType.KeyDown
                    if (triggers[0] && triggers[1]) {
                        triggers.fill(false)
                        state.openSearch()
                    }
                    return@onPreviewKeyEvent true
                }
                when {
                    event.type != KeyEventType.KeyDown -> false
                    else -> {
                        val before = state.cursorSignature
                        val menuBefore = state.menuOpen
                        val moreBefore = state.moreOpen
                        val typingBefore = state.textInputActive
                        val keyboardBefore = (state.searchOpen && !state.searchOnResults) || state.ownKeyboardEntry
                        val onShiftBefore = state.keyboardOnShift
                        val screenBefore = state.screen
                        val idleBack = (event.key == Key.ButtonB || event.key == Key.Back || event.key == Key.Escape) && !state.canStepBack()
                        val handled = state.onKey(event.key, isRepeat = event.nativeKeyEvent.repeatCount > 0)
                        val isDirection = event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                            event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                        // A push against an edge (the cursor didn't move) has the edge sound, once per press; a B with nothing to go back to is silent.
                        val atEdge = isDirection && state.cursorSignature == before
                        val silent = idleBack || atEdge
                        val edgeSound = handled && atEdge && !idleBack && event.nativeKeyEvent.repeatCount == 0 && !typingBefore &&
                            !keyboardBefore && state.detailsGame == null
                        if (EdgeSoundOn && edgeSound && settings.menuSounds) uiSounds.play(Sfx.Edge)
                        if (handled && !silent && settings.menuSounds) {
                            val overlay = state.overlayOpen
                            when {
                                // Confirming or cancelling a text entry (title, new folder, search field).
                                typingBefore -> uiSounds.play(Sfx.CancelConfirm)
                                // Pressing a key of the search keyboard has its own sound.
                                // R1 switches between the letters and the number pad, with its own sound.
                                keyboardBefore && (event.key == Key.ButtonR1 || event.key == Key.ButtonL1) -> Unit
                                // Shift toggles the capitals with the keyboard sound (played by the state, so L1 gets it too).
                                keyboardBefore && onShiftBefore && (event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.DirectionCenter) -> Unit
                                keyboardBefore && (event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.DirectionCenter ||
                                    event.key == Key.ButtonX || event.key == Key.ButtonY) -> uiSounds.play(Sfx.Keyboard)
                                // Opening the launcher menu; leaving it with B is silent (choosing an entry plays its own sound).
                                !moreBefore && state.moreOpen -> uiSounds.play(Sfx.Menu)
                                // Choosing an entry of the launcher menu: the confirm sound (the random game has its own launch sound).
                                moreBefore && (event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.DirectionCenter) ->
                                    if (state.moreIndex != 2) uiSounds.play(Sfx.CancelConfirm)
                                moreBefore && !state.moreOpen && (event.key == Key.ButtonB || event.key == Key.Back || event.key == Key.Escape) -> Unit
                                // Opening the settings menu or switching between Favorites / All Games / Apps; closing the menu is silent.
                                state.menuOpen && !menuBefore -> uiSounds.play(Sfx.Menu)
                                state.screen != screenBefore -> uiSounds.play(Sfx.MenuNavigate)
                                menuBefore && !state.menuOpen -> Unit
                                // Page changes play their own sound (so the right stick and touch swipes get it too); at the last or first page it is the edge sound.
                                event.key == Key.ButtonL1 || event.key == Key.ButtonR1 ->
                                    if (EdgeSoundOn && state.cursorSignature == before && event.nativeKeyEvent.repeatCount == 0) uiSounds.play(Sfx.Edge)
                                // A on the home screens launches something, which has its own sound.
                                (event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.DirectionCenter) && !overlay -> Unit
                                // Enter on an item of a menu.
                                event.key == Key.ButtonA || event.key == Key.Enter || event.key == Key.DirectionCenter -> uiSounds.play(Sfx.CancelConfirm)
                                else -> uiSounds.play(Sfx.Navigate)
                            }
                        }
                        handled
                    }
                }
            }
    ) {
        // Everything but the ambient clock sits in one box, so the clock's blur reaches the menus and dialogs too.
        Box(Modifier.fillMaxSize().centerBlur { ambient.value }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Behind an overlay the blurred screen is rendered once into a texture and just composited, instead of
                // being blurred again every frame (the background is held still meanwhile, see below).
                .graphicsLayer { compositingStrategy = if (overlayOpenForBlur) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                .then(if (backdropBlur > 0.dp) Modifier.blur(backdropBlur) else Modifier)
                .progressiveBlur(20.dp) { launchFade }
                // The launch is a step in: the screen swells around the tile you launched as it fades away, and settles back as it returns.
                .graphicsLayer {
                    val zoom = 1f + LaunchZoom * launchFade
                    scaleX = zoom
                    scaleY = zoom
                    val origin = launchOrigin(state)
                    transformOrigin = TransformOrigin(origin.x / Dimens.DesignWidth, origin.y / Dimens.DesignHeight)
                }
                .centerBlur { bootAppear.value * (1f - boot.value) }
                .spotBlur(LevelCenterX, LevelCenterY, LevelBlurRadius, LevelBlurRadius * LevelHeightShare, maxBlur = 26.dp) { levelAlpha }
        ) {
            // The focus blink holds still behind an overlay, so the blurred picture underneath doesn't change.
            val staticPulse = remember { { 1f } }
            CompositionLocalProvider(LocalFocusPulse provides (if (overlayOpenForBlur) staticPulse else LocalFocusPulse.current)) {
            val shownPage = when (state.screen) {
                HomeScreen.Favorites -> HomePage.Favorites
                HomeScreen.AllGames -> HomePage.AllGames
                HomeScreen.Apps -> HomePage.Apps
            }
            // The Minima ribbon is one wave behind both All Games and Apps (just as bright on both), so switching between them doesn't
            // redraw it or restart it; it fades out over Favorites. The original wave is drawn by the pages themselves.
            val ribbon = settings.waveStyle == WaveRibbon
            val ribbonLevel by animateFloatAsState(
                when (shownPage) {
                    HomePage.AllGames, HomePage.Apps -> 1f
                    HomePage.Favorites -> 0f
                },
                tween(350),
                label = "ribbonLevel"
            )
            if (ribbon && ribbonLevel > 0.001f) {
                XmbWave(style = WaveRibbon, config = settings.wave, brightness = ribbonLevel, pulses = pulses, modifier = Modifier.fillMaxSize())
            }
            val pageWave = if (ribbon) WaveOff else settings.waveStyle
            BlurFadeSwitch(
                targetState = shownPage,
                // Favorites is home: the libraries are one step in (and back out again); All Games and Apps lie side by side.
                motion = { from, to ->
                    when {
                        from == HomePage.Favorites -> SwitchMotion.Forward
                        to == HomePage.Favorites -> SwitchMotion.Back
                        to == HomePage.Apps -> SwitchMotion.Left
                        else -> SwitchMotion.Right
                    }
                }
            ) { page ->
                when (page) {
                    HomePage.Favorites -> BlurFadeSwitch(
                        targetState = state.favView,
                        // A folder or Recently Played is one step in from Favorites.
                        motion = { from, to ->
                            when {
                                from == FavView.Favorites -> SwitchMotion.Forward
                                to == FavView.Favorites -> SwitchMotion.Back
                                else -> SwitchMotion.None
                            }
                        }
                    ) { view ->
                        // Switching between Favorites, Recently Played and a folder blurs and fades the page over.
                        BentoScreen(
                            pageCount = state.pageCountIn(view),
                            entriesFor = { p -> state.pageOf(view, p) },
                            slotsFor = { p -> state.slotsIn(view, p) },
                            page = state.favoritesNav.page,
                            slot = state.favoritesNav.slot,
                            editMode = state.editMode,
                            held = state.held,
                            label = if (state.editMode) "Edit Mode" else "Favorites",
                            onPageChange = { target -> state.changeActivePage(target - state.favoritesNav.page) },
                            pressTick = state.pressTick,
                            logoAlpha = boot.value,
                            onTap = state::tapFavorite,
                            onSwipe = state::swipeFavoritesPage,
                            onEditHold = { state.touchEditHold(HomeScreen.Favorites, it) },
                            onEditDrag = { state.touchEditDrag(HomeScreen.Favorites, it) },
                            onEditEdge = { state.touchEditEdge(HomeScreen.Favorites, it) },
                            onEditRelease = state::touchEditRelease,
                            showLauncher = view == FavView.Favorites,
                            viewLabel = state.labelIn(view),
                            viewIcon = when (view) {
                                FavView.Recent -> com.minimaui.app.R.drawable.ic_history
                                is FavView.Folder -> com.minimaui.app.R.drawable.ic_folder
                                FavView.Favorites -> null
                            },
                        )
                    }
                    HomePage.AllGames -> AllGamesScreen(
                        platforms = state.visiblePlatforms,
                        platformIndex = state.selectedPlatformIndex,
                        gamesFor = state::gamesFor,
                        gameIndexFor = state::gameIndexFor,
                        showPlus = { (it.id == "android" || it.id == "steam") && state.editMode },
                        platformIcon = library::platformIcon,
                        platformTitle = library::platformTitle,
                        wave = pageWave,
                        ribbon = ribbon,
                        pressTick = state.pressTick,
                        editMode = state.editMode,
                        held = state.held,
                        onTapPlatform = state::touchPlatform,
                        onTapGame = state::touchGame,
                        onSwipe = state::swipeXmb,
                        onEditHoldPlatform = state::touchEditHoldPlatform,
                        onEditDragPlatform = state::touchEditDragPlatform,
                        onEditRelease = state::touchEditRelease,
                    )
                    HomePage.Apps -> AppsScreen(
                        apps = state.apps,
                        page = state.appsNav.page,
                        slot = state.appsNav.slot,
                        pageCount = state.appsNav.pageCount,
                        editMode = state.editMode,
                        held = state.held,
                        wave = pageWave,
                        ribbon = ribbon,
                        pressTick = state.pressTick,
                        onTap = state::tapApp,
                        onSwipe = state::swipeAppsPage,
                        onEditHold = { state.touchEditHold(HomeScreen.Apps, it) },
                        onEditDrag = { state.touchEditDrag(HomeScreen.Apps, it) },
                        onEditEdge = { state.touchEditEdge(HomeScreen.Apps, it) },
                        onEditRelease = state::touchEditRelease,
                    )
                }
            }

            // The pill sits in the middle on Favorites and in the corner on the libraries; moving between them it does what the screens
            // do (the new one grows in, the old one swells away), not a slide.
            BlurFadeSwitch(
                targetState = shownPage == HomePage.Favorites,
                modifier = Modifier.fillMaxSize(),
                motion = { _, atHome -> if (atHome) SwitchMotion.Back else SwitchMotion.Forward }
            ) { atHome ->
                Box(Modifier.fillMaxSize()) {
                    StatusPill(
                        docked = { if (atHome) 0f else 1f },
                        modifier = Modifier.offset {
                            IntOffset((if (atHome) PillCenterX else PillCornerX).dp.roundToPx(), Dimens.StatusPillTop.roundToPx())
                        }
                    )
                }
            }

            // Same layer as the status pill, so page changes don't touch it. It steps aside (fades) while a menu or
            // dialog is up, since those show their own hints in the same corner.
            val overlayUp = state.overlayOpen
            val hintAlpha = animateFloatAsState(if (overlayUp) 0f else 1f, tween(160), label = "hintAlpha")
            ControlHintBar(
                hints = state.hints,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.HintBarEnd, bottom = Dimens.HintBarBottom)
                    .graphicsLayer { alpha = hintAlpha.value }
            )
            }
        }

        AnimatedVisibility(
            visible = state.menuOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            OverlayMotion {
                MenuOverlay(
                    category = state.menuCategory,
                    focus = state.menuFocus,
                    page = state.menuPage,
                    entriesFor = { p -> state.menuEntriesFor(state.menuCategory, p) },
                    pages = state.menuPageCount,
                    selected = state.menuAction,
                    hints = state.menuHints,
                    editTitle = state.menuEditTitle,
                    onTapCategory = state::touchMenuCategory,
                    onTapEntry = state::touchMenuEntry,
                    onTapOutside = state::touchMenuOutside,
                    onSwipePage = state::touchMenuPage,
                    onSeekVolume = state::touchSetVolume,
                    onStepList = state::touchMenuStep,
                    onSeekList = state::touchMenuSeek
                )
            }
        }

        AnimatedVisibility(
            visible = state.pickerOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            OverlayMotion {
                AppPicker(
                    title = state.pickerTitle,
                    items = state.pickerItems,
                    selected = state.pickerIndex,
                    hints = state.pickerHints,
                    onTapItem = state::touchPicker,
                    onTapOutside = state::touchPickerOutside,
                    onSwipeRows = state::touchPickerRows,
                    onSeekRows = state::touchPickerSeek
                )
            }
        }

        AnimatedVisibility(
            visible = state.detailsGame != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            val game = state.detailsGame
            if (game != null) {
                OverlayMotion {
                    GameDetailsDialog(
                        title = game.title,
                        hoursPlayed = state.playTimeText(game),
                        favorite = game.isFavorite,
                        platform = state.platformName(game),
                        modifier = Modifier.pointerInput(Unit) { detectTapGestures { state.touchCloseDetails() } }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.searchOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            OverlayMotion {
                SearchDialog(
                    query = state.searchQuery,
                    results = state.searchHits.map { it.title },
                    selected = state.searchIndex,
                    onResults = state.searchOnResults,
                    keyFocus = state.searchKey,
                    digits = state.searchDigits,
                    caps = state.searchCaps,
                    hints = state.searchHints,
                    onTapKey = state::touchSearchKey,
                    onTapResult = state::touchSearchResult,
                    onTapOutside = state::touchSearchOutside,
                    onStepResults = state::touchSearchStep,
                    onSeekResults = state::touchSearchSeek
                )
            }
        }

        AnimatedVisibility(
            visible = state.moreOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            OverlayMotion {
                FavoritesMoreMenu(
                    selected = state.moreIndex,
                    hints = state.moreHints,
                    onTapSlot = state::tapMore,
                    onTapOutside = { state.back() }
                )
            }
        }

        AnimatedVisibility(
            visible = state.folderPickerOpen && !state.newFolderEntry,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            OverlayMotion {
                FolderChooser(
                    title = state.folderPickerTitle,
                    names = state.folderNames,
                    selected = state.folderIndex,
                    hints = state.folderHints,
                    onTapRow = state::tapFolder,
                    onTapOutside = { state.back() },
                    onStepRows = state::touchFolderStep,
                    onSeekRows = state::touchFolderSeek
                )
            }
        }

        // The dialog keeps its look while it fades out (the state it reads has already been reset by then).
        val entryStyle = remember { mutableStateOf(false to "") }
        if (state.isEditingTitle) entryStyle.value = state.addingGameHubGame to state.titleEditHeading
        val ownEntry = remember { mutableStateOf(false) }
        if (state.isEditingTitle) ownEntry.value = state.ownKeyboardEntry
        AnimatedVisibility(
            visible = state.isEditingTitle,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            OverlayMotion {
                if (ownEntry.value) {
                    TitleKeyboardDialog(
                        heading = entryStyle.value.second,
                        text = state.entryText,
                        selected = state.entrySelected,
                        keyFocus = state.entryKey,
                        caps = state.entryCaps,
                        digits = state.entryDigits,
                        onTapKey = state::touchEntryKey
                    )
                } else {
                    TitleEditDialog(
                        value = state.titleEditValue,
                        onValueChange = state::updateTitleEdit,
                        onSave = { if (settings.menuSounds) uiSounds.play(Sfx.CancelConfirm); state.saveTitleEdit() },
                        heading = entryStyle.value.second,
                        searchStyle = entryStyle.value.first
                    )
                }
            }
        }

        LevelBackdrop(alpha = { levelAlpha })
        LevelSlider(
            label = (if (state.levelKind == LevelKind.Volume) "Volume" else "Brightness") + ": ${kotlin.math.round(levelFraction.coerceIn(0f, 1f) * 100f).toInt()}%",
            fraction = levelFraction,
            alpha = { levelAlpha }
        )
        ToastPill(text = state.toastText, alpha = { toastAlpha })
        }

        // While the screen is dimmed after a while without input, an analog clock shows over it (a tap or button wakes it).
        AmbientClock(visible = dimmed, fade = ambient)

        if (boot.value < 1f) BootLogo(progress = boot.value, appear = bootAppear.value)

        // A button press skips the video (touch does not, and is not passed on to the screen under it).
        if (bootVideo != null && videoPhase != BootVideoPhase.Gone) {
            BootVideo(
                uri = bootVideo,
                playing = videoPhase == BootVideoPhase.Playing,
                volume = settings.volume / 100f,
                onEnded = { failed ->
                    if (videoPhase == BootVideoPhase.Playing) videoPhase = if (failed) BootVideoPhase.Failed else BootVideoPhase.Finished
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = videoAlpha.value }
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            )
        }

        if (launchFade > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = launchFade }.background(OledBlack))
        }
    }
    }
}

/** The middle of the focused tile (design px): what a launch zooms in on. */
private fun launchOrigin(state: HomeState): Offset = when (state.screen) {
    HomeScreen.AllGames -> Offset(ListCenterX, FocusCenterY)
    HomeScreen.Apps -> BentoLayouts.Apps.getOrNull(state.appsNav.slot)?.let { Offset(it.centerX, it.centerY) }
    HomeScreen.Favorites -> state.slotsIn(state.favView, state.favoritesNav.page).getOrNull(state.favoritesNav.slot)
        ?.let { Offset(Dimens.PanelX.value + it.centerX, Dimens.PanelY.value + it.centerY) }
} ?: Offset(Dimens.DesignWidth / 2f, Dimens.DesignHeight / 2f)
