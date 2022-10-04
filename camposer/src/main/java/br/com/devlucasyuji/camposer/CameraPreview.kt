package br.com.devlucasyuji.camposer

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import br.com.devlucasyuji.camposer.androidview.onCameraTouchEvent
import br.com.devlucasyuji.camposer.extensions.clamped
import br.com.devlucasyuji.camposer.extensions.roundTo
import br.com.devlucasyuji.camposer.focus.FocusTap
import br.com.devlucasyuji.camposer.focus.SquareCornerFocus
import br.com.devlucasyuji.camposer.state.CamSelector
import br.com.devlucasyuji.camposer.state.CameraState
import br.com.devlucasyuji.camposer.state.CaptureMode
import br.com.devlucasyuji.camposer.state.FlashMode
import br.com.devlucasyuji.camposer.state.ImageAnalyzer
import br.com.devlucasyuji.camposer.state.ImplementationMode
import br.com.devlucasyuji.camposer.state.ScaleType
import br.com.devlucasyuji.camposer.state.rememberCameraState
import kotlinx.coroutines.delay
import androidx.camera.core.CameraSelector as CameraXSelector

/**
 * Creates a Camera Preview's composable.
 *
 * @param cameraState camera state hold some states and camera's controller, it can be useful to given action like [CameraState.takePicture]
 * @param camSelector camera selector to be added, default is back
 * @param captureMode camera capture mode, default is image
 * @param flashMode flash mode to be added, default is off
 * @param scaleType scale type to be added, default is fill center
 * @param enableTorch enable torch from camera, default is false.
 * @param zoomRatio zoom ratio to be added, default is 1.0
 * @param imageAnalyzer image analyzer from camera, see [ImageAnalyzer]
 * @param implementationMode implementation mode to be added, default is performance
 * @param isImageAnalysisEnabled enable or disable image analysis
 * @param isFocusOnTapEnabled turn on feature focus on tap if true
 * @param isPinchToZoomEnabled turn on feature pinch to zoom if true
 * @param onPreviewStreamChanged dispatch when preview is switching to front or back
 * @param onSwitchToFront composable preview when change camera to front and it's not been streaming yet
 * @param onSwitchToBack composable preview when change camera to back and it's not been streaming yet
 * @param onZoomRatioChanged dispatch when zoom is changed by pinch to zoom
 * @param focusTapContent content of focus tap, default is [SquareCornerFocus]
 * @param content content composable within of camera preview.
 * @see ImageAnalyzer
 * @see CameraState
 * */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraState: CameraState = rememberCameraState(),
    camSelector: CamSelector = cameraState.camSelector,
    captureMode: CaptureMode = cameraState.captureMode,
    flashMode: FlashMode = cameraState.flashMode,
    scaleType: ScaleType = cameraState.scaleType,
    enableTorch: Boolean = cameraState.enableTorch,
    zoomRatio: Float = cameraState.currentZoom,
    imageAnalyzer: ImageAnalyzer? = null,
    implementationMode: ImplementationMode = cameraState.implementationMode,
    isImageAnalysisEnabled: Boolean = cameraState.isImageAnalysisEnabled,
    isFocusOnTapEnabled: Boolean = cameraState.isFocusOnTapEnabled,
    isPinchToZoomEnabled: Boolean = cameraState.isPinchToZoomEnabled,
    onPreviewStreamChanged: () -> Unit = {},
    onSwitchToFront: @Composable (Bitmap) -> Unit = { bitmap ->
        BlurImage(
            modifier = Modifier.fillMaxSize(),
            bitmap = bitmap,
            radius = 20.dp,
            contentDescription = null
        )
    },
    onSwitchToBack: @Composable (Bitmap) -> Unit = { bitmap ->
        BlurImage(
            modifier = Modifier.fillMaxSize(),
            bitmap = bitmap,
            radius = 20.dp,
            contentDescription = null
        )
    },
    onZoomRatioChanged: (Float) -> Unit = {},
    focusTapContent: @Composable () -> Unit = { SquareCornerFocus() },
    content: @Composable () -> Unit = {},
) {
    val cameraIsInitialized by rememberUpdatedState(cameraState.isInitialized)

    CameraPreviewImpl(
        modifier = modifier,
        cameraState = cameraState,
        cameraIsInitialized = cameraIsInitialized,
        camSelector = camSelector,
        captureMode = captureMode,
        flashMode = flashMode,
        scaleType = scaleType,
        enableTorch = enableTorch,
        zoomRatio = zoomRatio,
        imageAnalyzer = imageAnalyzer,
        isImageAnalysisEnabled = isImageAnalysisEnabled,
        implementationMode = implementationMode,
        isFocusOnTapEnabled = isFocusOnTapEnabled,
        isPinchToZoomEnabled = isPinchToZoomEnabled,
        onZoomRatioChanged = onZoomRatioChanged,
        focusTapContent = focusTapContent,
        onPreviewStreamChanged = onPreviewStreamChanged,
        onSwipeToFront = onSwitchToFront,
        onSwipeToBack = onSwitchToBack,
        content = content
    )
}

@SuppressLint("RestrictedApi")
@Composable
internal fun CameraPreviewImpl(
    modifier: Modifier,
    cameraState: CameraState,
    cameraIsInitialized: Boolean,
    camSelector: CamSelector,
    captureMode: CaptureMode,
    flashMode: FlashMode,
    scaleType: ScaleType,
    enableTorch: Boolean,
    zoomRatio: Float,
    implementationMode: ImplementationMode,
    imageAnalyzer: ImageAnalyzer?,
    isImageAnalysisEnabled: Boolean,
    isFocusOnTapEnabled: Boolean,
    isPinchToZoomEnabled: Boolean,
    onZoomRatioChanged: (Float) -> Unit,
    onPreviewStreamChanged: () -> Unit,
    onSwipeToFront: @Composable (Bitmap) -> Unit,
    onSwipeToBack: @Composable (Bitmap) -> Unit,
    focusTapContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleEvent by lifecycleOwner.lifecycle.observeAsState()
    var tapOffset by remember { mutableStateOf(Offset.Zero) }
    val isCameraIdle by rememberUpdatedState(!cameraState.isStreaming)
    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }

    AndroidView(modifier = modifier, factory = { context ->
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            controller = cameraState.controller.apply {
                bindToLifecycle(lifecycleOwner)
            }

            previewStreamState.observe(lifecycleOwner) { state ->
                cameraState.isStreaming = state == PreviewView.StreamState.STREAMING
            }
        }
    }, update = { previewView ->
        if (cameraIsInitialized) {
            with(previewView) {
                this.scaleType = scaleType.type
                this.implementationMode = implementationMode.value
                onCameraTouchEvent(
                    onTap = { if (isFocusOnTapEnabled) tapOffset = it },
                    onScaleChanged = {
                        if (isPinchToZoomEnabled) {
                            // TODO pass min and max zoom as parameter
                            val zoom = zoomRatio.clamped(it).roundTo(1).coerceIn(1F, 10F)
                            onZoomRatioChanged(zoom)
                        }
                    }
                )
                latestBitmap = when {
                    lifecycleEvent == Lifecycle.Event.ON_STOP -> null
                    camSelector != cameraState.camSelector -> bitmap
                    else -> latestBitmap
                }
            }

            cameraState.camSelector = camSelector
            cameraState.captureMode = captureMode
            cameraState.scaleType = scaleType
            cameraState.isImageAnalysisEnabled = isImageAnalysisEnabled
            cameraState.imageAnalyzer = imageAnalyzer?.analyzer
            cameraState.implementationMode = implementationMode
            cameraState.isFocusOnTapEnabled = isFocusOnTapEnabled
            cameraState.isPinchToZoomEnabled = false
            cameraState.flashMode = flashMode
            cameraState.enableTorch = enableTorch
            cameraState.setZoomRatio(zoomRatio)
        }
    })

    FocusTap(
        offset = tapOffset,
        onAfterFocus = {
            delay(1000L)
            tapOffset = Offset.Zero
        },
    ) { focusTapContent() }

    if (isCameraIdle) {
        latestBitmap?.let {
            when (camSelector.selector.lensFacing) {
                CameraXSelector.LENS_FACING_FRONT -> onSwipeToFront(it)
                CameraXSelector.LENS_FACING_BACK -> onSwipeToBack(it)
                else -> Unit
            }
            LaunchedEffect(latestBitmap) {
                onPreviewStreamChanged()
                if (latestBitmap != null) onZoomRatioChanged(1F)
            }
        }
    }

    content()
}
