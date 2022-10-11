package com.tonakanyan.easyphotopicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import java.io.IOException

class EasyImage private constructor(
    private val context: Context,
    private val chooserTitle: String,
    private val folderName: String,
    private val allowMultiple: Boolean,
    private val chooserType: ChooserType,
    private val copyImagesToPublicGalleryFolder: Boolean
) {

    private var lastCameraFile: MediaFile? = null

    interface Callbacks {
        fun onImagePickerError(error: Throwable, source: MediaSource)

        fun onMediaFilesPicked(imageFiles: Array<MediaFile>, source: MediaSource)

        fun onCanceled(source: MediaSource)
    }

    private class ActivityCaller(val fragment: Fragment? = null, val activity: Activity? = null) {

        val context: Context
            get() = (activity ?: fragment?.activity)!!
    }

    private fun getCallerActivity(caller: Any): ActivityCaller? = when (caller) {
        is Activity -> ActivityCaller(activity = caller)
        is Fragment -> ActivityCaller(fragment = caller)
        else -> null
    }

    private fun startChooser(activityResultLauncher: ActivityResultLauncher<Intent>) {
        cleanup()
        try {
            lastCameraFile = Files.createCameraPictureFile(context)
            val intent = Intents.createChooserIntent(
                context = context,
                chooserTitle = chooserTitle,
                chooserType = chooserType,
                cameraFileUri = lastCameraFile!!.uri,
                allowMultiple = allowMultiple
            )
            activityResultLauncher.launch(intent)
        } catch (error: IOException) {
            error.printStackTrace()
            cleanup()
        }
    }

    private fun startDocuments(activityResultLauncher: ActivityResultLauncher<Intent>) {
        cleanup()
        val intent = Intents.createDocumentsIntent()
        activityResultLauncher.launch(intent)
    }

    private fun startGallery(activityResultLauncher: ActivityResultLauncher<Intent>) {
        cleanup()
        val intent = Intents.createGalleryIntent(allowMultiple)
        activityResultLauncher.launch(intent)
    }

    private fun startCameraForImage(activityResultLauncher: ActivityResultLauncher<Intent>) {
        cleanup()
        lastCameraFile = Files.createCameraPictureFile(context)
        val takePictureIntent =
            Intents.createCameraForImageIntent(context, lastCameraFile!!.uri)
        val capableComponent = takePictureIntent.resolveActivity(context.packageManager)?.also {
            activityResultLauncher.launch(takePictureIntent)
        }
        if (capableComponent == null) {
            Log.e(EASYIMAGE_LOG_TAG, "No app capable of handling camera intent")
            cleanup()
        }
    }

    private fun startCameraForVideo(activityResultLauncher: ActivityResultLauncher<Intent>) {
        cleanup()
        lastCameraFile = Files.createCameraVideoFile(context)
        val recordVideoIntent = Intents.createCameraForVideoIntent(context, lastCameraFile!!.uri)
        val capableComponent = recordVideoIntent.resolveActivity(context.packageManager)?.also {
            activityResultLauncher.launch(recordVideoIntent)
        }
        if (capableComponent == null) {
            Log.e(EASYIMAGE_LOG_TAG, "No app capable of handling camera intent")
            cleanup()
        }
    }

    fun openChooser(activityResultLauncher: ActivityResultLauncher<Intent>) = startChooser(activityResultLauncher)

    fun openDocuments(activityResultLauncher: ActivityResultLauncher<Intent>) = startDocuments(activityResultLauncher)

    fun openGallery(activityResultLauncher: ActivityResultLauncher<Intent>) = startGallery(activityResultLauncher)

    fun openCameraForImage(activityResultLauncher: ActivityResultLauncher<Intent>) = startCameraForImage(activityResultLauncher)

    fun openCameraForVideo(activityResultLauncher: ActivityResultLauncher<Intent>) = startCameraForVideo(activityResultLauncher)

//    fun handleActivityResult(requestCode: Int, resultCode: Int, resultIntent: Intent?, activity: Activity, callbacks: Callbacks) {
//        // EasyImage request codes are set to be between 374961 and 374965.
//        if (requestCode !in 34961..34965) return
//
//        val mediaSource = when (requestCode) {
//            RequestCodes.PICK_PICTURE_FROM_DOCUMENTS -> MediaSource.DOCUMENTS
//            RequestCodes.PICK_PICTURE_FROM_GALLERY -> MediaSource.GALLERY
//            RequestCodes.TAKE_PICTURE -> MediaSource.CAMERA_IMAGE
//            RequestCodes.CAPTURE_VIDEO -> MediaSource.CAMERA_VIDEO
//            else -> MediaSource.CHOOSER
//        }
//
//        if (resultCode == Activity.RESULT_OK) {
//            if (requestCode == RequestCodes.PICK_PICTURE_FROM_DOCUMENTS && resultIntent != null) {
//                onPickedExistingPicturesFromLocalStorage(resultIntent, activity, callbacks)
//            } else if (requestCode == RequestCodes.PICK_PICTURE_FROM_GALLERY && resultIntent != null) {
//                onPickedExistingPictures(resultIntent, activity, callbacks)
//            } else if (requestCode == RequestCodes.PICK_PICTURE_FROM_CHOOSER) {
//                onFileReturnedFromChooser(resultIntent, activity, callbacks)
//            } else if (requestCode == RequestCodes.TAKE_PICTURE) {
//                onPictureReturnedFromCamera(activity, callbacks)
//            } else if (requestCode == RequestCodes.CAPTURE_VIDEO) {
//                onVideoReturnedFromCamera(activity, callbacks)
//            }
//        } else {
//            removeCameraFileAndCleanup()
//            callbacks.onCanceled(mediaSource)
//        }
//    }

    //PICK_PICTURE_FROM_DOCUMENTS
    fun onPickedExistingPicturesFromLocalStorage(resultIntent: Intent, activity: Activity, callbacks: Callbacks) {
        Log.d(EASYIMAGE_LOG_TAG, "Existing picture returned from local storage")
        try {
            val uri = resultIntent.data!!
            val photoFile = Files.pickedExistingPicture(activity, uri)
            val mediaFile = MediaFile(uri, photoFile)
            callbacks.onMediaFilesPicked(arrayOf(mediaFile), MediaSource.DOCUMENTS)
        } catch (error: Throwable) {
            error.printStackTrace()
            callbacks.onImagePickerError(error, MediaSource.DOCUMENTS)
        }
        cleanup()
    }

    //PICK_PICTURE_FROM_GALLERY
    fun onPickedExistingPictures(resultIntent: Intent, activity: Activity, callbacks: Callbacks) {
        try {
            val clipData = resultIntent.clipData
            if (clipData != null) {
                Log.d(EASYIMAGE_LOG_TAG, "Existing picture returned")
                val files = mutableListOf<MediaFile>()
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    val file = Files.pickedExistingPicture(activity, uri)
                    files.add(MediaFile(uri, file))
                }
                if (files.isNotEmpty()) {
                    callbacks.onMediaFilesPicked(files.toTypedArray(), MediaSource.GALLERY)
                } else {
                    callbacks.onImagePickerError(
                        EasyImageException("No files were returned from gallery"),
                        MediaSource.GALLERY
                    )
                }
                cleanup()
            } else {
                onPickedExistingPicturesFromLocalStorage(resultIntent, activity, callbacks)
            }
        } catch (error: Throwable) {
            cleanup()
            error.printStackTrace()
            callbacks.onImagePickerError(error, MediaSource.GALLERY)
        }
    }

    //TAKE_PICTURE
    fun onPictureReturnedFromCamera(activity: Activity, callbacks: Callbacks) {
        Log.d(EASYIMAGE_LOG_TAG, "Picture returned from camera")
        lastCameraFile?.let { cameraFile ->
            try {
                if (cameraFile.uri.toString().isEmpty()) Intents.revokeWritePermission(
                    activity,
                    cameraFile.uri
                )
                val files = mutableListOf(cameraFile)
                if (copyImagesToPublicGalleryFolder) Files.copyFilesInSeparateThread(
                    activity,
                    folderName,
                    files.map { it.file })
                callbacks.onMediaFilesPicked(files.toTypedArray(), MediaSource.CAMERA_IMAGE)
            } catch (error: Throwable) {
                error.printStackTrace()
                callbacks.onImagePickerError(
                    EasyImageException("Unable to get the picture returned from camera.", error),
                    MediaSource.CAMERA_IMAGE
                )
            }
        }
        cleanup()
    }

    //CAPTURE_VIDEO
    fun onVideoReturnedFromCamera(activity: Activity, callbacks: Callbacks) {
        Log.d(EASYIMAGE_LOG_TAG, "Video returned from camera")
        lastCameraFile?.let { cameraFile ->
            try {
                if (cameraFile.uri.toString().isEmpty()) Intents.revokeWritePermission(
                    activity,
                    cameraFile.uri
                )
                val files = mutableListOf(cameraFile)
                if (copyImagesToPublicGalleryFolder) Files.copyFilesInSeparateThread(
                    activity,
                    folderName,
                    files.map { it.file })
                callbacks.onMediaFilesPicked(files.toTypedArray(), MediaSource.CAMERA_VIDEO)
            } catch (error: Throwable) {
                error.printStackTrace()
                callbacks.onImagePickerError(
                    EasyImageException("Unable to get the picture returned from camera.", error),
                    MediaSource.CAMERA_IMAGE
                )
            }
        }
        cleanup()
    }

    //PICK_PICTURE_FROM_CHOOSER
    fun onFileReturnedFromChooser(resultIntent: Intent?, activity: Activity, callbacks: Callbacks) {
        Log.d(EASYIMAGE_LOG_TAG, "File returned from chooser")
        if (resultIntent != null && !Intents.isTherePhotoTakenWithCameraInsideIntent(resultIntent) && resultIntent.data != null) {
            onPickedExistingPictures(resultIntent, activity, callbacks)
            removeCameraFileAndCleanup()
        } else if (lastCameraFile != null) {
            onPictureReturnedFromCamera(activity, callbacks)
        }
    }

    fun canDeviceHandleGallery(): Boolean {
        return Intents.plainGalleryPickerIntent().resolveActivity(context.packageManager) != null
    }

    private fun removeCameraFileAndCleanup() {
        lastCameraFile?.file?.let { file ->
            Log.d(EASYIMAGE_LOG_TAG, "Removing camera file of size: ${file.length()}")
            file.delete()
            Log.d(EASYIMAGE_LOG_TAG, "Clearing reference to camera file")
            lastCameraFile = null
        }
    }

    private fun cleanup() {
        lastCameraFile?.let { cameraFile ->
            Log.d(
                EASYIMAGE_LOG_TAG,
                "Clearing reference to camera file of size: ${cameraFile.file.length()}"
            )
            lastCameraFile = null
        }
    }

    class Builder(private val context: Context) {
        companion object {
            private fun getAppName(context: Context): String = try {
                context.applicationInfo.loadLabel(context.packageManager).toString()
            } catch (error: Throwable) {
                Log.e(
                    EASYIMAGE_LOG_TAG,
                    "App name couldn't be found. Probably no label was specified in the AndroidManifest.xml. Using EasyImage as a folder name for files."
                )
                error.printStackTrace()
                "EasyImage"
            }
        }

        private var chooserTitle: String = ""
        private var folderName: String = getAppName(context)
        private var allowMultiple = false
        private var chooserType: ChooserType = ChooserType.CAMERA_AND_DOCUMENTS
        private var copyImagesToPublicGalleryFolder: Boolean = false

        fun setChooserTitle(chooserTitle: String): Builder {
            this.chooserTitle = chooserTitle
            return this
        }

        fun setFolderName(folderName: String): Builder {
            this.folderName = folderName
            return this
        }

        fun setChooserType(chooserType: ChooserType): Builder {
            this.chooserType = chooserType
            return this
        }

        fun allowMultiple(allowMultiple: Boolean): Builder {
            this.allowMultiple = allowMultiple
            return this
        }

        fun setCopyImagesToPublicGalleryFolder(copyImagesToPublicGalleryFolder: Boolean): Builder {
            this.copyImagesToPublicGalleryFolder = copyImagesToPublicGalleryFolder
            return this
        }

        fun build(): EasyImage {
            return EasyImage(
                context = context,
                chooserTitle = chooserTitle,
                folderName = folderName,
                chooserType = chooserType,
                allowMultiple = allowMultiple,
                copyImagesToPublicGalleryFolder = copyImagesToPublicGalleryFolder
            )
        }
    }
}