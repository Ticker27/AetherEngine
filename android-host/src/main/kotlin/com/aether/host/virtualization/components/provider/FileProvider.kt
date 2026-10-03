package com.aether.host.virtualization.components.provider

import androidx.core.content.FileProvider as AndroidXFileProvider

/** AndroidX's root-confined URI provider, registered only under host-owned authorities. */
open class FileProvider : AndroidXFileProvider() {
    /** Legacy manifest alias retained for integrations that expect FileProvider$a. */
    class a : FileProvider()

    /** Legacy manifest alias retained for integrations that expect FileProvider$b. */
    class b : FileProvider()
}
