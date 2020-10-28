package com.tonakanyan.easyphotopicker

abstract class DefaultCallback : EasyImage.Callbacks {

    override fun onImagePickerError(error: Throwable, source: MediaSource) {}

    override fun onCanceled(source: MediaSource) {}
}
