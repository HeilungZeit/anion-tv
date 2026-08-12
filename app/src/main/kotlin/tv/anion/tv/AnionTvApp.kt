package tv.anion.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import tv.anion.tv.di.AppContainer

class AnionTvApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}
