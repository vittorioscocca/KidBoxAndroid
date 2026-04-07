package it.vittorioscocca.kidbox.ui.screens.home

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import it.vittorioscocca.kidbox.ui.navigation.AppDestination

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.onHeroPhotoSelected(it, context) }
    }
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB)),
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(10f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, Color.White, CircleShape)
                        .clip(CircleShape)
                        .clickable { onNavigate(AppDestination.Profile.route) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.avatarUrl != null) {
                        AsyncImage(
                            model = state.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color(0xFF666666))
                    }
                }
                IconButton(
                    onClick = {
                        Log.d("NAV", "Settings tapped, navigating to: ${AppDestination.Settings.route}")
                        onNavigate(AppDestination.Settings.route)
                    },
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF1A1A1A))
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
            Text(
                "KidBox",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = Color(0xFF1A1A1A),
            )
            Text(state.todayLabel, color = Color(0xFF666666))
            Spacer(modifier = Modifier.size(16.dp))

            FamilyHeroCard(
                familyName = state.familyName.ifBlank { "La tua famiglia" },
                dateLabel = state.todayLabel,
                members = state.memberCount,
                photoLocalPath = state.heroPhotoLocalPath,
                photoUrl = state.heroPhotoUrl,
                onTap = { onNavigate(AppDestination.FamilyPhotos.createRoute(state.familyId)) },
                onChangePhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            if (state.isUploadingHero) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            state.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                Text(err, color = Color(0xFFE53E3E), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.size(16.dp))

            val features = featureItems(state.familyId)
            features.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        FeatureCard(
                            item = item,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(item.route) },
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
        }

        HomeFab(
            expanded = state.isFabExpanded,
            actions = state.topQuickActions,
            onToggle = viewModel::toggleFab,
            onAction = { action ->
                viewModel.recordQuickAction(action)
                viewModel.closeFab()
                onNavigate(quickActionRoute(action, state.familyId))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )

    }
}

private data class FeatureItem(
    val title: String,
    val subtitle: String,
    val route: String,
    val icon: ImageVector,
    val cardColor: Color,
    val iconColor: Color,
)

@Composable
private fun FamilyHeroCard(
    familyName: String,
    dateLabel: String,
    members: Int,
    photoLocalPath: String?,
    photoUrl: String?,
    onTap: () -> Unit,
    onChangePhoto: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val localModel = photoLocalPath?.let { path ->
                val f = File(path)
                if (f.exists()) f else null
            }
            val imageModel = localModel ?: photoUrl
            if (imageModel != null) {
                AsyncImage(model = imageModel, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFDDDDDD)))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(dateLabel, color = Color.White, fontSize = 12.sp)
                Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(20.dp)) {
                    Text(
                        "$members membri",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                    )
                }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    familyName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Surface(color = Color.White.copy(alpha = 0.18f), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onChangePhoto)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tocca per cambiare foto", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    item: FeatureItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = item.cardColor),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(item.icon, contentDescription = null, tint = item.iconColor, modifier = Modifier.size(28.dp))
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1A1A1A))
            Text(item.subtitle, color = Color(0xFF666666), fontSize = 12.sp)
        }
    }
}

@Composable
private fun HomeFab(
    expanded: Boolean,
    actions: List<HomeQuickAction>,
    onToggle: () -> Unit,
    onAction: (HomeQuickAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fab_rotation")
    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (expanded) {
            actions.forEach { action ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                        Text(action.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFBF40),
                        modifier = Modifier.size(40.dp).clickable { onAction(action) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(actionIcon(action), contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = Color(0xFFFF6B00),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFFFFBF40), Color(0xFFF26118)),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

private fun featureItems(familyId: String): List<FeatureItem> = listOf(
    FeatureItem("Note", "Appunti veloci", AppDestination.NotesHome.createRoute(familyId), Icons.AutoMirrored.Filled.Note, Color(0xFFFFF9E6), Color(0xFFF5A623)),
    FeatureItem("To-Do", "Lista condivisa", AppDestination.Todo.route, Icons.Filled.CheckCircle, Color(0xFFEBF3FF), Color(0xFF2E86FF)),
    FeatureItem("Lista Spesa", "Lista condivisa", AppDestination.ShoppingList.createRoute(familyId), Icons.Filled.LocalGroceryStore, Color(0xFFEDFAF3), Color(0xFF27AE60)),
    FeatureItem("Calendario", "Eventi e affidamenti", AppDestination.Calendar.createRoute(familyId), Icons.Filled.CalendarMonth, Color(0xFFF3EEFF), Color(0xFF8B5CF6)),
    FeatureItem("Salute", "Health tracker", AppDestination.PediatricChildSelector.createRoute(familyId), Icons.Filled.Favorite, Color(0xFFFFEAEA), Color(0xFFE53E3E)),
    FeatureItem("Chat", "Messaggi famiglia", AppDestination.Chat.route, Icons.AutoMirrored.Filled.Chat, Color(0xFFEDFAF3), Color(0xFF27AE60)),
    FeatureItem("Spese", "Rette, visite, extra", AppDestination.ExpensesHome.createRoute(familyId), Icons.Filled.Euro, Color(0xFFFFF3E6), Color(0xFFFF6B00)),
    FeatureItem("Documenti", "Carte importanti", AppDestination.DocumentsHome.route, Icons.Filled.Description, Color(0xFFEBF3FF), Color(0xFF2E86FF)),
    FeatureItem("Posizione", "Dove sono tutti", AppDestination.FamilyLocation.createRoute(familyId), Icons.Filled.Place, Color(0xFFE6FAF8), Color(0xFF00BFA5)),
    FeatureItem("Foto e Video", "Ricordi famiglia", AppDestination.FamilyPhotos.createRoute(familyId), Icons.Filled.PhotoLibrary, Color(0xFFFFF0F5), Color(0xFFE91E8C)),
    FeatureItem("Assistente AI", "Chiedi aiuto", AppDestination.AskExpert.route, Icons.Filled.Psychology, Color(0xFFEEF0FF), Color(0xFF5C6BC0)),
    FeatureItem("Family", "Gestisci famiglia", AppDestination.FamilySettings.route, Icons.Filled.Person, Color(0xFFFFF3E6), Color(0xFFFF6B00)),
)

private fun actionIcon(action: HomeQuickAction): ImageVector = when (action) {
    HomeQuickAction.EXPENSE -> Icons.Filled.Euro
    HomeQuickAction.EVENT -> Icons.Filled.CalendarMonth
    HomeQuickAction.TODO -> Icons.Filled.CheckCircle
    HomeQuickAction.NOTE -> Icons.AutoMirrored.Filled.Note
    HomeQuickAction.SHOPPING_LIST -> Icons.Filled.LocalGroceryStore
    HomeQuickAction.MESSAGE -> Icons.AutoMirrored.Filled.Chat
    HomeQuickAction.HEALTH -> Icons.Filled.Favorite
    HomeQuickAction.DOCUMENTS -> Icons.Filled.Description
}

private fun quickActionRoute(action: HomeQuickAction, familyId: String): String = when (action) {
    HomeQuickAction.EXPENSE -> AppDestination.ExpensesHome.createRoute(familyId)
    HomeQuickAction.EVENT -> AppDestination.Calendar.createRoute(familyId)
    HomeQuickAction.TODO -> AppDestination.Todo.route
    HomeQuickAction.NOTE -> AppDestination.NotesHome.createRoute(familyId)
    HomeQuickAction.SHOPPING_LIST -> AppDestination.ShoppingList.createRoute(familyId)
    HomeQuickAction.MESSAGE -> AppDestination.Chat.route
    HomeQuickAction.HEALTH -> AppDestination.PediatricChildSelector.createRoute(familyId)
    HomeQuickAction.DOCUMENTS -> AppDestination.DocumentsHome.route
}
