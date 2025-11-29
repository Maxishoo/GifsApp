package com.example.gifsapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyScreen()
        }
    }
}

data class CatImage(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int
)

class MyViewModel : ViewModel() {
    var state by mutableStateOf(false)
        private set

    var last by mutableIntStateOf(0)
        private set

    val elements = mutableStateListOf<CatImage>()

    fun toggleState(value: Int) {
        last = value
        state = true
    }

    fun hideOverlay() {
        state = false
    }

    fun addElement() {
        viewModelScope.launch {
            try {
                val newCat = loadCatImage()
                elements.add(newCat)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("CatAPI", "Ошибка загрузки котика", e)
            }
        }
    }

    fun removeElement() {
        elements.removeLastOrNull()
    }

    private suspend fun loadCatImage(): CatImage {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.thecatapi.com/v1/images/search")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                val jsonArray = JSONArray(responseBody)
                val jsonObject = jsonArray.getJSONObject(0)
                CatImage(
                    id = jsonObject.getString("id"),
                    url = jsonObject.getString("url"),
                    width = jsonObject.getInt("width"),
                    height = jsonObject.getInt("height")
                )
            } else {
                CatImage(
                    id = "error",
                    url = "",
                    width = 500,
                    height = 500
                )

            }
        }
    }
}

@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    val elements by viewModel::elements
    val state by viewModel::state
    val last by viewModel::last

    if (state) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.background))
                .windowInsetsPadding(WindowInsets.statusBars)
                .clickable { viewModel.hideOverlay() }
        ) {
            if (last < elements.size) {
                val cat = elements[last]
                AsyncImage(
                    model = cat.url,
                    contentDescription = "Cat $last",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = "$last",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = colorResource(R.color.main_text),
                fontSize = 24.sp,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorResource(id = R.color.background))
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(count = integerResource(id = R.integer.columns_count)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(
                        start = 10.dp, end = 10.dp,
                        bottom = 100.dp
                    )
            ) {
                items(
                    items = elements,
                    key = { it.id }
                ) { cat ->
                    CatSquare(cat, {
                        viewModel.toggleState(elements.indexOf(cat))
                    })
                }

            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(110.dp)
                    .padding(
                        top = 30.dp,
                        bottom = 30.dp
                    ),
            ) {
                BottomButton({ viewModel.addElement() }, stringResource(R.string.Add_button))
                BottomButton(
                    { viewModel.removeElement() },
                    stringResource(R.string.Remove_button)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MyScreenPreview() {
    MyScreen()
}

@Composable
private fun BottomButton(action: () -> Unit, text: String) {
    Button(
        onClick = action,
        modifier = Modifier
            .width(190.dp)
            .padding(
                start = 5.dp,
                end = 5.dp
            ),

        colors = ButtonDefaults.buttonColors(
            containerColor = colorResource(id = R.color.button)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            color = colorResource(R.color.main_text),
            fontSize = 34.sp,
        )
    }
}

@Composable
private fun CatSquare(
    cat: CatImage,
    action: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { action() }
    ) {
        AsyncImage(
            model = cat.url,
            contentDescription = "Cat ${cat.id}",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.placeholder), // Добавьте placeholder в ресурсы
            //error = painterResource(R.drawable.error) // Добавьте error изображение в ресурсы
        )

        // Показываем номер поверх изображения
        Text(
            text = "${cat.id.take(4)}", // Показываем первые 4 символа ID
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(4.dp)
        )
    }
}