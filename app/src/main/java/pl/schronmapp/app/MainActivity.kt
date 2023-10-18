package pl.schronmapp.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.clustering.Clustering
import dev.jeziellago.compose.markdowntext.MarkdownText
import pl.schronmapp.app.ui.theme.Background
import pl.schronmapp.app.ui.theme.Maintext
import pl.schronmapp.app.ui.theme.SchronMappTheme
import pl.schronmapp.app.ui.theme.Subtext
import pl.schronmapp.app.ui.theme.jetBrainsMonoFamily
import java.io.File

fun findArticleById(articleId: Int?, articles: List<Article>): Article?{
    return articles.find {it.id == articleId}
}

class MainActivity : ComponentActivity(){
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        installSplashScreen()
        setContent {
            SchronMappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(26,26,26)
                ) {
                    val articles = listOf(
                        Article(1,"Sygnały alarmowe", "o których nie pamiętamy", painterResource(id = R.drawable.signals_icon)),
                        Article(2,"Jak postępować", "gdy wybuchnie bomba", painterResource(id = R.drawable.feet_icon)),
                        Article(3,"Pierwsza pomoc", "w sytuacji ataku nuklearnego", painterResource(id = R.drawable.med_icon)),
                        Article(4,"Ekwipunek", "na wypadek sytuacji awaryjnej", painterResource(id = R.drawable.backpack_icon)),
                        Article(5,"Zapas żywności", "na czarną godzinę", painterResource(id = R.drawable.food_icon)),
                        Article(6,"Schronienie", "w atakach nuklearnych", painterResource(id = R.drawable.home_icon)),
                    )
                    Navigation(articles)
                }
            }
        }
    }

}

data class ParkItem(
    val itemPosition: LatLng,
    val itemTitle: String,
    val itemSnippet: String
) : ClusterItem {
    override fun getPosition(): LatLng =
        itemPosition

    override fun getTitle(): String =
        itemTitle

    override fun getSnippet(): String =
        itemSnippet
}

@Composable
fun Navigation(articles: List<Article>){

    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = "main"
    ){

        composable("guide") {
            GuideScreen(navController, context, articles)
        }
        composable("main") {
            MainScreen(navController, context)
        }
        composable(
            route = "article/{articleId}",
            arguments = listOf(navArgument("articleId") { type = NavType.IntType } )
        ) {backStackEntry  ->
            val articleId = backStackEntry.arguments?.getInt("articleId")
            val selectedArticle = findArticleById(articleId,articles)
            if (selectedArticle != null) {
                ArticleScreen(article = selectedArticle, navController = navController)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(navController: NavController, context: Context){
    var nearestShelter by remember { mutableStateOf<ParkItem?>(null) }

    val allLocationPermissionState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)
    )

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var parkMarkers by remember {
        mutableStateOf(
            mutableStateListOf(
                ParkItem(LatLng(52.23179429052197, 21.006023094833036), "Pałac Kultury i Nauki", "10 000"),
                ParkItem(LatLng(52.221379960406544, 21.00741331387573), "Politechnika Warszawska", "400"),
                ParkItem(LatLng(52.131290106588054, 21.065775217127108), "Metro Kabaty", "14 250"),
                ParkItem(LatLng(52.14030507481344, 21.057339156019438), "Metro Natolin", "10 100"),
                ParkItem(LatLng(52.149701862684495, 21.045479310508302), "Metro Imielin", "10 100"),
                ParkItem(LatLng(52.15619818832474, 21.034597177033238), "Metro Sokłosy", "10 050"),
                ParkItem(LatLng(52.16193822292176, 21.02765676768401), "Metro Ursynów", "14 900"),
                ParkItem(LatLng(52.17274051754404, 21.02626895204445), "Metro Służew", "11 000"),
                ParkItem(LatLng(52.181626128532805, 21.02290758183306), "Metro Wilanowska", "15 500"),
                ParkItem(LatLng(52.19003054356013, 21.016938430961407), "Metro Wierzbno", "10 900"),
                ParkItem(LatLng(54.3565535243318, 18.64431858303589), "Gdańsk Główny Peron 3", "430"),
                ParkItem(LatLng(54.53363092131768, 18.484839726539036), "Schron Leszczynki", "400"),
                ParkItem(LatLng(54.371968539714246, 18.634253217251075), "Budynek C200", "300"),
                ParkItem(LatLng(53.418815787047954, 14.550484607708615), "Szczecin Główny", "5 000"),
                ParkItem(LatLng(54.37657390109219, 18.625938852970176), "Szpital kliniczny", "1 500"),
            )
        )
    }

    var cameraPositionState by remember {
        mutableStateOf(
            CameraPositionState(
                CameraPosition.fromLatLngZoom(
                    LatLng(54.3565535243318, 18.64431858303589),
                    10f
                )
            )
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            allLocationPermissionState.launchMultiplePermissionRequest()
        }

        if (allLocationPermissionState.allPermissionsGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLocation = LatLng(location.latitude, location.longitude)
                }
            }
        }


        userLocation?.let { location ->
            // Dodaj marker z lokalizacją użytkownika na mapie
            Marker(
                state = MarkerState(location),
                title = "Twoja lokalizacja",
                snippet = "Jesteś tutaj"

            )

            Circle(
                center = LatLng(location.latitude, location.longitude),
                radius = 1000.0,
                fillColor = Color(22, 22, 22, 55),
                strokeColor = Color.Transparent,
            )
        }

        nearestShelter?.let { shelter ->
            val polylineOptions = PolylineOptions().add(userLocation, shelter.itemPosition)
            polylineOptions.color(Color.Blue.toArgb())
            polylineOptions.width(5f)

        }

        Clustering(
            items = parkMarkers
        )
    }

    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = nearestShelter?.itemTitle ?: "Gdynia",
            modifier = Modifier
                .padding(15.dp)
                .background(Background, shape = RoundedCornerShape(8.dp))
                .padding(8.dp)
        )

        Column (verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Nawiguj",
                fontSize = 20.sp,
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .background(Background, shape = RoundedCornerShape(8.dp))
                    .padding(15.dp)
                    .clickable {
                        nearestShelter =
                            findNearestShelter(userLocation, parkMarkers)
                    }
            )
            Row (
                modifier = Modifier
                    .border(1.dp, Subtext, RoundedCornerShape(12.dp))
                    .background(Background, shape = RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                IconButton(
                    onClick = { /*TODO*/ },
                    Modifier
                        .padding(10.dp)
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(Icons.Rounded.LocationOn,contentDescription = null, tint = Maintext, modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.width(20.dp))
                IconButton(
                    onClick = {
                        navController.navigate("guide")
                    },
                    Modifier
                        .padding(10.dp)
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(Icons.Rounded.Info,contentDescription = null, tint = Subtext, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

fun findNearestShelter(
    userLocation: LatLng?,
    shelters: List<ParkItem>
): ParkItem? {
    if (userLocation == null) return null

    val userLocationLocation = Location("UserLocation")
    userLocationLocation.latitude = userLocation.latitude
    userLocationLocation.longitude = userLocation.longitude

    var nearestShelter: ParkItem? = null
    var minDistance = Double.MAX_VALUE

    for (shelter in shelters) {
        val shelterLocation = Location("ShelterLocation")
        shelterLocation.latitude = shelter.itemPosition.latitude
        shelterLocation.longitude = shelter.itemPosition.longitude

        val distance = userLocationLocation.distanceTo(shelterLocation)
        if (distance < minDistance) {
            minDistance = distance.toDouble()
            nearestShelter = shelter
        }
    }


    return nearestShelter
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(navController: NavController, context: Context, articles: List<Article>){

    var selectedArticle by remember {mutableStateOf<Article?>(null)}
    var articleSearch by remember {mutableStateOf("")}

    Column (horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {

        TextField(
            value = articleSearch,
            onValueChange = { articleSearch = it },
            placeholder = { Text("Podaj listę najważniejszych artykułów w zapasie żywności") },
            label = { Text(text = "Dopytaj asystenta", color = Color.White) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(27.dp)
        ){
            val gridItems = articles.chunked(2)
            items(gridItems){rowOfGames ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ){
                    for (article in rowOfGames){
                        ArticleTab(article = article, onClick = {
                            selectedArticle = it
                            navController.navigate("article/${article.id}")
                        }, Modifier.fillMaxHeight())
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter){
        Row (
            modifier = Modifier
                .padding(bottom = 20.dp)
                .border(1.dp, Subtext, RoundedCornerShape(12.dp))
                .background(Background, shape = RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            IconButton(
                onClick = {
                    navController.navigate("main")
                },
                Modifier
                    .padding(10.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Icon(Icons.Rounded.LocationOn,contentDescription = null, tint = Subtext, modifier = Modifier.fillMaxSize())
            }
            Spacer(modifier = Modifier.width(20.dp))
            IconButton(
                onClick = {
                    navController.navigate("guide")
                },
                Modifier
                    .padding(10.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Icon(Icons.Rounded.Info,contentDescription = null, tint = Maintext, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun ArticleTab(article: Article, onClick: (Article) -> Unit, modifier: Modifier){

    Column (
        modifier = Modifier
            .padding(10.dp)
            .background(color = Color(44, 44, 44, 255), shape = RoundedCornerShape(5))
            .padding(14.dp)
            .clickable { onClick(article) }
    ) {
        Image(
            painter = article.image,
            contentDescription = article.name,
            modifier = Modifier
                .height(150.dp)
                .scale(1.5f)
                .align(alignment = Alignment.CenterHorizontally)
                .padding(10.dp)
        )
        Text(text = article.name, Modifier.width(150.dp))
        Text(text = article.desc, Modifier.width(150.dp), color = Subtext, fontFamily = jetBrainsMonoFamily)
    }
    
}

@Composable
fun ArticleScreen(article: Article, navController: NavController){
    Box(modifier = Modifier.fillMaxSize()){
        Button(onClick = { (navController.navigate("guide")) }) {

        }
    }

    Column {
        Text(text = article.name, fontSize = 30.sp, color = Subtext, modifier = Modifier.padding(20.dp))
        val file = ArticleFromFile(context = LocalContext.current, article.id)
        MarkdownFormatting(markdownContent = file)
    }
}

fun ArticleFromFile(context: Context, articleId: Int): String {
    val resourceId = context.resources.getIdentifier("content$articleId", "raw", context.packageName)
    if (resourceId != 0) {
        return context.resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    }
    return "Błąd odczytu pliku."
}

@Composable
fun MarkdownFormatting(markdownContent: String){
    MarkdownText(
        markdown = markdownContent,
        color = Maintext
    )
}


