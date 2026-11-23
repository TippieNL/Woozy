package app.woozy.ui.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import app.woozy.BuildConfig
import app.woozy.R
import app.woozy.domain.model.LocationFix
import app.woozy.domain.model.MapOrientation
import app.woozy.domain.model.Place
import app.woozy.domain.model.ReportType
import app.woozy.domain.model.TrafficReport
import app.woozy.domain.model.UserSettings
import app.woozy.ui.reports.style
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.ImageContent
import org.maplibre.android.maps.ImageStretches
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Owns the MapLibre [MapView] and everything imperative about it: style, user puck, camera
 * follow modes, markers and the road-name lookup. Compose talks to it through plain methods so
 * GPS updates never trigger recomposition of the map.
 */
class MapController(private val context: Context) {

    val mapView: MapView
    private var map: MapLibreMap? = null
    private var styleLoaded = false
    private val density = context.resources.displayMetrics.density

    private val _isFollowing = MutableStateFlow(false)

    /** False once the user pans the map; the UI then shows the recenter button. */
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    var onRoadNameChanged: ((String?) -> Unit)? = null

    private var settings = UserSettings()
    private var lastFix: LocationFix? = null
    private var lastBearing: Float? = null
    private var showingGpsArrow = false
    private var initialCameraDone = false
    private var lastRoadQueryAt = 0L
    private var lastRoadName: String? = null
    private var reports: List<TrafficReport> = emptyList()
    private var destination: Place? = null
    private var viewport = Viewport()

    // Lifecycle bookkeeping so dispose never double-calls MapView callbacks.
    private var started = false
    private var resumed = false
    private var destroyed = false

    data class Viewport(val heightPx: Int = 0, val topInsetPx: Int = 0, val bottomObscuredPx: Int = 0)

    init {
        val options = MapLibreMapOptions.createFromAttributes(context)
            .logoEnabled(false)
            .attributionEnabled(false) // attribution is rendered in Compose (always visible, as ODbL requires)
            .compassEnabled(false)
            .foregroundLoadColor(0xFF0B0C0E.toInt())
        mapView = MapView(context, options)
        mapView.onCreate(null)
        mapView.getMapAsync(::onMapReady)
    }

    // region lifecycle
    fun onStart() { if (!started && !destroyed) { mapView.onStart(); started = true } }
    fun onResume() { if (!resumed && !destroyed) { mapView.onResume(); resumed = true } }
    fun onPause() { if (resumed) { mapView.onPause(); resumed = false } }
    fun onStop() { if (started) { mapView.onStop(); started = false } }
    fun onDestroy() {
        if (destroyed) return
        onPause(); onStop()
        mapView.onDestroy()
        destroyed = true
        map = null
    }
    // endregion

    private fun onMapReady(map: MapLibreMap) {
        this.map = map
        map.setMinZoomPreference(3.0)
        map.setMaxZoomPreference(20.0)
        map.uiSettings.apply {
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = false
        }
        // Start over the Netherlands-scale view until the first fix arrives.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(52.1, 5.3), 6.5))

        val builder = Style.Builder()
            .fromJson(loadStyleJson())
            .withImage(SHIELD_IMAGE, shieldBitmap(), false, shieldStretchX(), shieldStretchY(), shieldContent())
            .withImage(DESTINATION_IMAGE, pinBitmap())
        ReportType.entries.forEach { builder.withImage(reportIconId(it), reportMarkerBitmap(it)) }
        map.setStyle(builder, ::onStyleLoaded)
    }

    private fun onStyleLoaded(style: Style) {
        style.addSource(GeoJsonSource(REPORTS_SOURCE))
        style.addLayer(
            SymbolLayer(REPORTS_LAYER, REPORTS_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get(PROP_ICON)),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconSize(
                    Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 0.6f), Expression.stop(15, 1.0f)),
                ),
            ),
        )
        style.addSource(GeoJsonSource(DESTINATION_SOURCE))
        style.addLayer(
            SymbolLayer(DESTINATION_LAYER, DESTINATION_SOURCE).withProperties(
                PropertyFactory.iconImage(DESTINATION_IMAGE),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        activateLocationComponent(style)
        styleLoaded = true
        pushReports()
        pushDestination(animate = false)
        lastFix?.let { updateLocation(it) }
    }

    private fun activateLocationComponent(style: Style) {
        val map = map ?: return
        val accent = ContextCompat.getColor(context, R.color.launcher_background)
        val options = LocationComponentOptions.builder(context)
            .foregroundTintColor(accent)
            .backgroundTintColor(android.graphics.Color.WHITE)
            .foregroundStaleTintColor(0xFF8A8F96.toInt())
            .backgroundStaleTintColor(android.graphics.Color.WHITE)
            .bearingTintColor(accent)
            .accuracyColor(accent)
            .accuracyAlpha(0.12f)
            .elevation(6f)
            .enableStaleState(true)
            .staleStateTimeout(10_000L)
            .pulseEnabled(false)
            .trackingGesturesManagement(true)
            .build()
        val activation = LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(options)
            // We feed fixes from LocationRepository (single GPS consumer for the whole app).
            .useDefaultLocationEngine(false)
            .build()
        map.locationComponent.apply {
            activateLocationComponent(activation)
            renderMode = RenderMode.NORMAL
            addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
                override fun onCameraTrackingDismissed() {
                    _isFollowing.value = false
                }

                override fun onCameraTrackingChanged(currentMode: Int) {
                    _isFollowing.value = currentMode != CameraMode.NONE
                }
            })
        }
    }

    private val locationComponent: LocationComponent?
        get() = map?.locationComponent?.takeIf { styleLoaded && it.isLocationComponentActivated }

    // region public API used by Compose

    @SuppressLint("MissingPermission") // Fixes only exist when permission is granted; no engine is attached.
    fun updateLocation(fix: LocationFix) {
        lastFix = fix
        val lc = locationComponent ?: return
        if (!lc.isLocationComponentEnabled) lc.isLocationComponentEnabled = true

        val speed = fix.speedMps ?: 0f
        val bearing = fix.bearingDegrees
        // GPS bearing is noise when (nearly) stationary: keep the last good one so the map doesn't spin.
        if (bearing != null && speed > 1.5f) lastBearing = bearing

        lc.forceLocationUpdate(
            Location(fix.provider ?: "woozy").apply {
                latitude = fix.latitude
                longitude = fix.longitude
                time = fix.timeMillis
                elapsedRealtimeNanos = fix.elapsedRealtimeNanos
                fix.accuracyMeters?.let { accuracy = it }
                fix.speedMps?.let { this.speed = it }
                lastBearing?.let { this.bearing = it }
            },
        )

        // Arrow while driving, dot while standing still (hysteresis avoids flicker at ~walking pace).
        val moving = lastBearing != null && speed > (if (showingGpsArrow) 1.0f else 2.5f)
        if (moving != showingGpsArrow) {
            showingGpsArrow = moving
            lc.renderMode = if (moving) RenderMode.GPS else RenderMode.NORMAL
        }

        if (!initialCameraDone) {
            initialCameraDone = true
            if (settings.autoFollow) {
                startFollowing(animate = false)
            } else {
                map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude, fix.longitude), FOLLOW_ZOOM))
            }
        }
        queryRoadName(fix)
    }

    fun applySettings(new: UserSettings) {
        val old = settings
        settings = new
        val cameraChanged = old.mapOrientation != new.mapOrientation || old.perspective3d != new.perspective3d
        when {
            new.autoFollow && !old.autoFollow -> startFollowing(animate = true)
            cameraChanged && _isFollowing.value -> startFollowing(animate = true)
        }
    }

    fun setViewport(viewport: Viewport) {
        if (viewport == this.viewport) return
        this.viewport = viewport
        if (_isFollowing.value) locationComponent?.paddingWhileTracking(trackingPadding(), 300L)
    }

    fun recenter() = startFollowing(animate = true)

    fun setReports(list: List<TrafficReport>) {
        reports = list
        pushReports()
    }

    fun setDestination(place: Place?) {
        if (place == destination) return
        destination = place
        pushDestination(animate = place != null)
        if (place == null && settings.autoFollow) startFollowing(animate = true)
    }

    // endregion

    private fun startFollowing(animate: Boolean) {
        val lc = locationComponent ?: return
        if (lastFix == null) return
        val mode = if (settings.mapOrientation == MapOrientation.HEADING_UP) CameraMode.TRACKING_GPS else CameraMode.TRACKING_GPS_NORTH
        val tilt = if (settings.perspective3d) FOLLOW_TILT else 0.0
        val bearing = if (mode == CameraMode.TRACKING_GPS_NORTH) 0.0 else null
        lc.paddingWhileTracking(trackingPadding(), 0L)
        lc.setCameraMode(mode, if (animate) 750L else 0L, FOLLOW_ZOOM, bearing, tilt, null)
        _isFollowing.value = true
    }

    /**
     * Places the puck in the visible part of the map (between status bar and bottom panel):
     * centred for north-up, lower third for direction-up so you see more road ahead.
     */
    private fun trackingPadding(): DoubleArray {
        val v = viewport
        if (v.heightPx <= 0) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val visible = (v.heightPx - v.topInsetPx - v.bottomObscuredPx).coerceAtLeast(1)
        val fraction = if (settings.mapOrientation == MapOrientation.HEADING_UP) 0.68 else 0.5
        val targetY = v.topInsetPx + fraction * visible
        val bottom = v.bottomObscuredPx.toDouble()
        val top = (2 * targetY - v.heightPx + bottom).coerceAtLeast(0.0)
        return doubleArrayOf(0.0, top, 0.0, bottom)
    }

    /**
     * Reads the name of the road under the GPS position from the vector tiles already on screen
     * (invisible "road-name-query" layer) — no network request, no API key.
     */
    private fun queryRoadName(fix: LocationFix) {
        val map = map ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRoadQueryAt < 1_000L) return
        lastRoadQueryAt = now
        val point = map.projection.toScreenLocation(LatLng(fix.latitude, fix.longitude))
        val r = 14f * density
        val features = runCatching {
            map.queryRenderedFeatures(RectF(point.x - r, point.y - r, point.x + r, point.y + r), ROAD_NAME_QUERY_LAYER)
        }.getOrDefault(emptyList())
        val name = features.firstNotNullOfOrNull { it.stringProp("name") }
            ?: features.firstNotNullOfOrNull { it.stringProp("ref") }
        if (name != lastRoadName) {
            lastRoadName = name
            onRoadNameChanged?.invoke(name)
        }
    }

    private fun Feature.stringProp(key: String): String? =
        if (hasNonNullValueForProperty(key)) getStringProperty(key)?.takeIf { it.isNotBlank() } else null

    private fun pushReports() {
        if (!styleLoaded) return
        val source = map?.style?.getSourceAs<GeoJsonSource>(REPORTS_SOURCE) ?: return
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                reports.map { report ->
                    Feature.fromGeometry(Point.fromLngLat(report.longitude, report.latitude)).apply {
                        addStringProperty(PROP_ICON, reportIconId(report.type))
                        addStringProperty("id", report.id)
                    }
                },
            ),
        )
    }

    private fun pushDestination(animate: Boolean) {
        if (!styleLoaded) return
        val map = map ?: return
        val source = map.style?.getSourceAs<GeoJsonSource>(DESTINATION_SOURCE) ?: return
        val place = destination
        if (place == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
            return
        }
        source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude)))
        if (animate) {
            // Showing a destination is an explicit "look over there": stop following.
            locationComponent?.cameraMode = CameraMode.NONE
            _isFollowing.value = false
            val v = viewport
            map.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(place.latitude, place.longitude))
                        .zoom(15.0)
                        .tilt(0.0)
                        .bearing(0.0)
                        .padding(0.0, v.topInsetPx.toDouble(), 0.0, v.bottomObscuredPx.toDouble())
                        .build(),
                ),
                900,
            )
        }
    }

    private fun loadStyleJson(): String =
        context.assets.open("map/woozy_dark.json").bufferedReader().use { it.readText() }
            .replace("{{TILES_URL}}", BuildConfig.MAP_STYLE_TILES_URL)
            .replace("{{GLYPHS_URL}}", BuildConfig.MAP_GLYPHS_URL)

    // region bitmaps

    private fun dp(value: Float) = value * density

    private fun reportMarkerBitmap(type: ReportType): Bitmap {
        val size = dp(38f).toInt()
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val c = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x66000000
        canvas.drawCircle(c, c + dp(1f), c - dp(1f), paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(c, c, c - dp(1.5f), paint)
        paint.color = type.style.color.toInt()
        canvas.drawCircle(c, c, c - dp(4f), paint)
        ContextCompat.getDrawable(context, type.style.icon)?.mutate()?.let { d ->
            d.setTint(android.graphics.Color.WHITE)
            val inset = dp(9.5f).toInt()
            d.setBounds(inset, inset, size - inset, size - inset)
            d.draw(canvas)
        }
        return bitmap
    }

    private fun pinBitmap(): Bitmap {
        val w = dp(40f).toInt()
        val bitmap = createBitmap(w, w)
        val canvas = Canvas(bitmap)
        ContextCompat.getDrawable(context, R.drawable.ic_pin)?.mutate()?.let { outline ->
            outline.setTint(android.graphics.Color.WHITE)
            outline.setBounds(0, 0, w, w)
            outline.draw(canvas)
        }
        ContextCompat.getDrawable(context, R.drawable.ic_pin)?.mutate()?.let { pin ->
            pin.setTint(ContextCompat.getColor(context, R.color.launcher_background))
            val inset = dp(3f).toInt()
            pin.setBounds(inset, inset / 2, w - inset, w - inset - inset / 2)
            pin.draw(canvas)
        }
        return bitmap
    }

    private val shieldW get() = dp(24f)
    private val shieldH get() = dp(18f)
    private val shieldR get() = dp(4f)

    private fun shieldBitmap(): Bitmap {
        val bitmap = createBitmap(shieldW.toInt(), shieldH.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F3F5.toInt() }
        Canvas(bitmap).drawRoundRect(RectF(0f, 0f, shieldW, shieldH), shieldR, shieldR, paint)
        return bitmap
    }

    private fun shieldStretchX() = listOf(ImageStretches(shieldR, shieldW - shieldR))
    private fun shieldStretchY() = listOf(ImageStretches(shieldR, shieldH - shieldR))
    private fun shieldContent() = ImageContent(shieldR, shieldR, shieldW - shieldR, shieldH - shieldR)

    // endregion

    companion object {
        private const val FOLLOW_ZOOM = 16.5
        private const val FOLLOW_TILT = 50.0
        private const val ROAD_NAME_QUERY_LAYER = "road-name-query"
        private const val SHIELD_IMAGE = "woozy-shield"
        private const val DESTINATION_IMAGE = "woozy-destination"
        private const val REPORTS_SOURCE = "woozy-reports"
        private const val REPORTS_LAYER = "woozy-reports-layer"
        private const val DESTINATION_SOURCE = "woozy-destination"
        private const val DESTINATION_LAYER = "woozy-destination-layer"
        private const val PROP_ICON = "icon"

        fun reportIconId(type: ReportType) = "woozy-report-${type.name.lowercase()}"
    }
}
