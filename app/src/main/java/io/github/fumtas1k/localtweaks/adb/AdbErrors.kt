package io.github.fumtas1k.localtweaks.adb

import java.io.IOException

/** Internal signal that credential recovery requires a clean process restart. */
internal class AdbRestartRequiredException : IOException(
    "ADB credentials require an app restart before pairing again",
)
