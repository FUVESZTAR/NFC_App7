package com.plantnfc.presentation

/** Translatable snackbar messages produced by ViewModels. */
sealed class SnackMsg {
    object NfcWriteSuccess              : SnackMsg()
    data class NfcWriteError(val detail: String) : SnackMsg()
    object Saved                        : SnackMsg()
    data class SaveFailed(val detail: String) : SnackMsg()
    object SelectPlantFirst             : SnackMsg()
    object GenerateFirst                : SnackMsg()
    object TapNfcTag                    : SnackMsg()
    object LocationPermRequired         : SnackMsg()
    object Copied                       : SnackMsg()
    object Synced                       : SnackMsg()
    object Imported                     : SnackMsg()
    object Refreshed                    : SnackMsg()
    data class SyncFailed(val detail: String) : SnackMsg()
    data class RefreshFailed(val detail: String) : SnackMsg()
}

/** GPS tracking status produced by GeneratorViewModel. */
sealed class GpsStatus {
    object Ready             : GpsStatus()
    object Fetching          : GpsStatus()
    data class Updating(val accuracyM: Int) : GpsStatus()
    object Locked            : GpsStatus()
    object NoFix             : GpsStatus()
    object PermissionDenied  : GpsStatus()
}
