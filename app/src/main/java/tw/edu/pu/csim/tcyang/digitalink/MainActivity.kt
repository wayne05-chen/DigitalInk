package tw.edu.pu.csim.tcyang.digitalink

import android.R.attr.x
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tw.edu.pu.csim.tcyang.digitalink.ui.theme.DigitalInkTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalInkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Doodle(
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
fun Doodle(modifier: Modifier = Modifier) {
    var recognizedText by remember {
        mutableStateOf("請在畫布區手寫，再按「開始辨識」按鈕")
    }

    var path by remember {
        mutableStateOf(
            Path().apply {
                moveTo(50f, 100f)
                lineTo(280f, 500f)
                lineTo(450f, 400f)
                close()
            }
        )
    }
    var inkBuilder by remember {
        mutableStateOf(Ink.builder())
    }

    var strokeBuilder by remember {
        mutableStateOf<Ink.Stroke.Builder?>(null)
    }

    // 用於觸發 Canvas 重繪的狀態計數
    var drawVersion by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .border(width = 2.dp, color = Color.Gray)
                .pointerInput(Unit) {
                    // 監聽拖曳手勢
                    detectDragGestures(
                        onDragStart = { offset ->
                            // 手指按下
                            val x = offset.x
                            val y = offset.y
                            val t = System.currentTimeMillis()  // 時間戳記
                            path.moveTo(x, y)
                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))

                        },
                        onDrag = { change, _ ->
                            // 手指移動
                            change.consume()
                            val x = change.position.x
                            val y = change.position.y
                            val t = System.currentTimeMillis()
                            path.lineTo(x, y)
                            val tempPath = Path()
                            tempPath.addPath(path)
                            path = tempPath
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                        },
                        onDragEnd = { /* 手指離開 */
                            // 手指離開：將打包好的 Stroke 加入 inkBuilder
                            strokeBuilder?.build()?.let { stroke ->
                                inkBuilder.addStroke(stroke)
                            }

                            // 重置當前 Stroke Builder 準備下一筆劃
                            strokeBuilder = null
                        }
                    )
                }
        ) {
            // 讀取 drawVersion 讓狀態變動時重新繪製
            @Suppress("UNUSED_EXPRESSION")
            drawVersion
            drawPath(
                path = path,
                color = Color.Black,
                alpha = 1f,
                style = Stroke(width = 10f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            // 開始辨識
            Button(onClick = {                    val ink = inkBuilder.build()   // 包含全部手寫軌跡的「欲辨識內容」
                if (model != null) {
                    RemoteModelManager.getInstance()
                        .download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {recognizer?.recognize(ink)
                            ?.addOnSuccessListener { result ->
                                if (result.candidates.isNotEmpty()) {
                                    val topCandidate = result.candidates[0].text
                                    recognizedText = "辨識結果: $topCandidate"
                                } else {
                                    recognizedText = "辨識結果: 無法辨識"
                                }
                            }
                            ?.addOnFailureListener { recognizedText = "辨識失敗: ${it.message}" }


                        }
                        .addOnFailureListener { recognizedText = "模型下載失敗" }
                } else {
                    recognizedText = "模型建立失敗"
                }

                if (ink.strokes.isEmpty()) {
                    recognizedText = "請先書寫內容"
                    return@Button
                }

                recognizedText = "正在處理中..."

                // TODO: 在此串接辨識邏輯並更新 recognizedText
            }) {
                Text("開始辨識")
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 清除畫布
            Button(onClick = {
                path = Path()
                inkBuilder = Ink.builder()
                strokeBuilder = null
                recognizedText = "請在畫布區手寫，再按「開始辨識」按鈕"

            }) {
                Text("清除")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(recognizedText)
    }
}
val modelIdentifier = remember {
    try {
        DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hant")
    } catch (e: Exception) {
        null
    }
}

// 建立語言模型物件
val model = remember(modelIdentifier) {
    modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
}

// 建立手寫辨識引擎
val recognizer = remember(model) {
    model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
}
