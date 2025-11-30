package com.example.gifsapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.valentinilk.shimmer.ShimmerBounds
import com.valentinilk.shimmer.rememberShimmer
import com.valentinilk.shimmer.shimmer

@Entity(tableName = "cached_cat_images")
data class CachedCatImage(
    @PrimaryKey val id: String,
    val url: String,
    val width: Int,
    val height: Int
)

@Dao
interface CachedCatImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(catImage: CachedCatImage)

    @Query("SELECT * FROM cached_cat_images WHERE id = :id")
    suspend fun getById(id: String): CachedCatImage?

    @Query("SELECT * FROM cached_cat_images ORDER BY rowid DESC")
    suspend fun getAll(): List<CachedCatImage>

    @Query("DELETE FROM cached_cat_images WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cached_cat_images")
    suspend fun clearAll()
}

@Database(
    entities = [CachedCatImage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedCatImageDao(): CachedCatImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cat_cache_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CatImageDataSource(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun fetchRandomCatImage(): CatImage {
        val request = Request.Builder()
            .url("https://api.thecatapi.com/v1/images/search  ")
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
            val jsonArray = JSONArray(responseBody)
            val jsonObject = jsonArray.getJSONObject(0)
            return CatImage(
                id = jsonObject.getString("id"),
                url = jsonObject.getString("url"),
                width = jsonObject.getInt("width"),
                height = jsonObject.getInt("height")
            )
        } else {
            throw Exception("Не удалось загрузить изображение котика")
        }
    }
}

class CatImageRepository(
    private val dataSource: CatImageDataSource = CatImageDataSource(),
    private val cachedCatImageDao: CachedCatImageDao
) {
    suspend fun getRandomCatImage(): CatImage {
        val remoteCat = dataSource.fetchRandomCatImage()

        // Сохраняем в кэш
        cachedCatImageDao.insert(
            CachedCatImage(
                id = remoteCat.id,
                url = remoteCat.url,
                width = remoteCat.width,
                height = remoteCat.height
            )
        )

        return remoteCat
    }

    suspend fun getCachedImages(): List<CatImage> {
        return cachedCatImageDao.getAll().map { cached ->
            CatImage(
                id = cached.id,
                url = cached.url,
                width = cached.width,
                height = cached.height
            )
        }
    }

    suspend fun clearAll() {
        cachedCatImageDao.clearAll()
    }
}

class MyViewModelFactory(
    private val repository: CatImageRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val dao = db.cachedCatImageDao()
        val repository = CatImageRepository(CatImageDataSource(), dao)

        enableEdgeToEdge()
        setContent {
            MyScreen(viewModel = viewModel(factory = MyViewModelFactory(repository)))
        }
    }
}

data class CatImage(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int
)

enum class LoadingState {
    IDLE, LOADING, ERROR
}

class MyViewModel(
    private val repository: CatImageRepository
) : ViewModel() {

    var state by mutableStateOf(false)
        private set

    var last by mutableIntStateOf(0)
        private set

    val elements = mutableStateListOf<CatImage>()
    var loadingState by mutableStateOf(LoadingState.IDLE)
    var isEndReached by mutableStateOf(false)

    init {
        viewModelScope.launch {
            val cached = repository.getCachedImages()
            elements.addAll(cached)
            if (cached.isEmpty()) {
                loadMoreCats()
            }
        }
    }

    fun loadMoreCats() {
        if (loadingState == LoadingState.LOADING || isEndReached) return

        loadingState = LoadingState.LOADING
        viewModelScope.launch {
            val newCats = mutableListOf<CatImage>()
            repeat(12) { index ->
                if (loadingState == LoadingState.LOADING) {
                    try {
                        val newCat = withContext(Dispatchers.IO) {
                            repository.getRandomCatImage()
                        }
                        newCats.add(newCat)
                    } catch (e: Exception) {
                        Log.e("MyViewModel", "Failed to load cat image at index $index", e)
                        loadingState = LoadingState.ERROR
                        return@launch
                    }
                }
            }
            if (loadingState == LoadingState.LOADING) {
                elements.addAll(newCats)
                loadingState = LoadingState.IDLE
            }
        }
    }

    fun toggleState(value: Int) {
        last = value
        state = true
    }

    fun hideOverlay() {
        state = false
    }

    fun clearAll() {
        elements.clear()
        viewModelScope.launch {
            repository.clearAll()
        }
        loadMoreCats()
    }
}

@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    val elements by viewModel::elements
    val state by viewModel::state
    val last by viewModel::last
    val loadingState by viewModel::loadingState

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

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
            if (elements.isEmpty() && loadingState == LoadingState.LOADING) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colorResource(id = R.color.button)
                    )
                }
            } else {
                val columnsCount = integerResource(id = R.integer.columns_count)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(
                            start = 10.dp,
                            end = 10.dp,
                            bottom = 100.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(count = columnsCount),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(
                            items = elements,
                            key = { it.id }
                        ) { cat ->
                            CatSquare(cat, {
                                viewModel.toggleState(elements.indexOf(cat))
                            })
                        }

                        if (loadingState == LoadingState.LOADING) {
                            item(span = { GridItemSpan(columnsCount) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .height(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = colorResource(id = R.color.button),
                                        strokeWidth = 4.dp
                                    )
                                }
                            }
                        }
                    }

                    if (loadingState == LoadingState.ERROR) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Ошибка загрузки", color = Color.Red)
                                Button(onClick = { viewModel.loadMoreCats() }) {
                                    Text("Повторить")
                                }
                            }
                        }
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
                    horizontalArrangement = Arrangement.Center
                ) {
                    BottomButton({ viewModel.clearAll() }, stringResource(R.string.Clear))
                }

                androidx.compose.runtime.LaunchedEffect(gridState) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { lastVisibleIndex ->
                            val totalItems = gridState.layoutInfo.totalItemsCount
                            if (lastVisibleIndex != null && lastVisibleIndex >= totalItems - 1 && loadingState == LoadingState.IDLE) {
                                viewModel.loadMoreCats()
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun BottomButton(action: () -> Unit, text: String) {
    Button(
        onClick = action,
        modifier = Modifier
            .width(220.dp)
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
    val shimmerInstance = rememberShimmer(
        shimmerBounds = ShimmerBounds.View
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { action() }
    ) {
        SubcomposeAsyncImage(
            model = cat.url,
            contentDescription = "Cat ${cat.id}",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmer(shimmerInstance)
                        .background(Color.LightGray)
                )
            },
        )
        Text(
            text = cat.id.take(4),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(4.dp)
        )
    }
}