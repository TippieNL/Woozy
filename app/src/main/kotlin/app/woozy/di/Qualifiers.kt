package app.woozy.di

import javax.inject.Qualifier

/** Application-lifetime coroutine scope (SupervisorJob + Dispatchers.Default). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
