package com.example.gifsapp

import android.os.Bundle
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyScreen()
        }
    }
}

class MyViewModel : ViewModel() {
    var state by mutableStateOf(false)
        private set

    var last by mutableIntStateOf(0)
        private set

    val elements = mutableStateListOf<Int>()

    fun toggleState(value: Int) {
        last = value
        state = true
    }

    fun hideOverlay() {
        state = false
    }

    fun addElement() {
        elements.add(elements.size)
    }

    fun removeElement() {
        elements.removeLastOrNull()
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
            Text(
                text = "$last",
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                color = colorResource(R.color.main_text),
                fontSize = 34.sp,
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
                    key = { it }
                ) { value ->
                    Square(value, {
                        viewModel.toggleState(value)
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
private fun Square(
    number: Int,
    action: () -> Unit,
    modifier: Modifier = Modifier
) {
    val col = if (number % 2 == 0) colorResource(R.color.red) else colorResource(R.color.blue)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(col)
            .clickable { action() }
    ) {
        Text(
            text = "$number",
            color = colorResource(R.color.main_text),
            fontSize = 34.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}