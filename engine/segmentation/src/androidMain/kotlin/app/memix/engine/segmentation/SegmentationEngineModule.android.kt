package app.memix.engine.segmentation

import app.memix.core.domain.segmentation.Segmenter
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val segmentationEngineModule = module {
    single<Segmenter> {
        MlKitSegmenter(
            clients = SubjectSegmenterClients(androidContext()),
            installer = SubjectModelInstaller(androidContext()),
            filesDir = androidContext().filesDir,
            ioDispatcher = Dispatchers.IO,
            cpuDispatcher = Dispatchers.Default,
            logger = get(),
        )
    }
}
