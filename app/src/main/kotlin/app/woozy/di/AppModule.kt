package app.woozy.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.woozy.BuildConfig
import app.woozy.data.api.PhotonApi
import app.woozy.data.api.ValhallaApi
import app.woozy.data.database.FavoriteDao
import app.woozy.data.database.RecentDestinationDao
import app.woozy.data.database.TrafficReportDao
import app.woozy.data.database.WoozyDatabase
import app.woozy.data.providers.GeocodingProvider
import app.woozy.data.providers.NoBackendReportDataSource
import app.woozy.data.providers.PhotonGeocodingProvider
import app.woozy.data.providers.ReportRemoteDataSource
import app.woozy.data.providers.RoutingProvider
import app.woozy.data.providers.ValhallaRoutingProvider
import app.woozy.data.providers.ValhallaSpeedLimitProvider
import app.woozy.data.providers.SpeedLimitProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WoozyDatabase =
        Room.databaseBuilder(context, WoozyDatabase::class.java, "woozy.db").build()

    @Provides
    fun provideTrafficReportDao(db: WoozyDatabase): TrafficReportDao = db.trafficReportDao()

    @Provides
    fun provideFavoriteDao(db: WoozyDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideRecentDestinationDao(db: WoozyDatabase): RecentDestinationDao = db.recentDestinationDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Request DTOs rely on defaults (e.g. Valhalla "costing": "auto"); they must be sent.
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 10L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Public OSM services require an identifying User-Agent. Generic on purpose: no user data.
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Woozy/${BuildConfig.VERSION_NAME} (Android navigation app)")
                    .build(),
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    okhttp3.logging.HttpLoggingInterceptor().setLevel(okhttp3.logging.HttpLoggingInterceptor.Level.BASIC),
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideValhallaApi(client: OkHttpClient, json: Json): ValhallaApi = Retrofit.Builder()
        .baseUrl(BuildConfig.ROUTING_BASE_URL)
        .client(
            // Long routes can take a few seconds on the shared public instance.
            client.newBuilder().readTimeout(30, TimeUnit.SECONDS).build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ValhallaApi::class.java)

    @Provides
    @Singleton
    fun providePhotonApi(client: OkHttpClient, json: Json): PhotonApi = Retrofit.Builder()
        .baseUrl(BuildConfig.GEOCODER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PhotonApi::class.java)
}

/** Provider bindings: swap implementations here (e.g. a real speed-limit or backend source). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProvidersModule {
    @Binds
    abstract fun bindGeocoder(impl: PhotonGeocodingProvider): GeocodingProvider

    @Binds
    abstract fun bindRoutingProvider(impl: ValhallaRoutingProvider): RoutingProvider

    @Binds
    abstract fun bindSpeedLimitProvider(impl: ValhallaSpeedLimitProvider): SpeedLimitProvider

    @Binds
    abstract fun bindReportRemote(impl: NoBackendReportDataSource): ReportRemoteDataSource
}
