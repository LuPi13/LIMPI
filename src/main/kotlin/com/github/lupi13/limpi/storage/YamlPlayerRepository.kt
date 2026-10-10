package com.github.lupi13.limpi.storage

import java.nio.file.Path

class YamlPlayerRepository(
    directory: Path,
    write: (Path, String) -> Unit = AtomicFiles::write,
    offlineCacheSize: Int = 256,
) : CachedPlayerRepository(YamlProfilePersistence(directory, write), offlineCacheSize)
