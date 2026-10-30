package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.auth.AuthManager
import com.example.ui.theme.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        var showOnboarding by remember { mutableStateOf(true) }
        var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
        var showWelcomeBanner by remember { mutableStateOf(false) }
        var currentLang by remember { mutableStateOf("ar") }
        var isAdminMode by remember { mutableStateOf(true) }
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val credentialManager = remember { CredentialManager.create(context) }

        // Observe Firebase Auth state
        DisposableEffect(Unit) {
          val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
          }
          Firebase.auth.addAuthStateListener(listener)
          onDispose {
            Firebase.auth.removeAuthStateListener(listener)
          }
        }

        if (showOnboarding) {
          OnboardingScreen(
            lang = currentLang,
            onToggleLang = { currentLang = if (currentLang == "ar") "en" else "ar" },
            onFinish = {
              showOnboarding = false
            }
          )
        } else if (currentUser == null) {
          AuthScreen(
            lang = currentLang,
            onGoogleSignIn = {
              AuthManager.signInWithGoogle(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  currentUser = Firebase.auth.currentUser
                  showWelcomeBanner = true
                },
                onAuthError = { err ->
                  currentUser = Firebase.auth.currentUser ?: run {
                    showWelcomeBanner = true
                    Firebase.auth.currentUser
                  }
                },
                onAuthCancelled = {},
                scope = coroutineScope
              )
            }
          )
        } else {
          Box(modifier = Modifier.fillMaxSize()) {
            MainAppScaffold(
              lang = currentLang,
              isAdminMode = isAdminMode,
              onToggleAdminMode = { isAdminMode = !isAdminMode },
              onLogout = {
                AuthManager.signOut(
                  context = context,
                  credentialManager = credentialManager,
                  onSignOutComplete = { currentUser = null },
                  scope = coroutineScope
                )
              }
            )

            if (showWelcomeBanner) {
              LaunchedEffect(Unit) {
                delay(2000)
                showWelcomeBanner = false
              }
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(MaqraahPrimary.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                  Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(80.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                  }
                  Spacer(modifier = Modifier.height(16.dp))
                  Text(
                    text = "أهلًا وسهلًا بك في مقرأة الإمام الجزري",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = "نسأل الله لك رحلة مباركة مع كتابه الكريم",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun OnboardingScreen(lang: String, onToggleLang: () -> Unit, onFinish: () -> Unit) {
  var pageIndex by remember { mutableIntStateOf(0) }
  val slides = listOf(
    Triple(
      "مرحبًا بك في مقرأة الإمام الجزري",
      "منارة قرآنية تُعنى بتعليم كتاب الله بالتلقي والإتقان، وربط الطلاب بأهل القرآن وأسانيد القراءة.",
      "رحلة علمية تبدأ بآية وتُثمر إتقانًا وسندًا"
    ),
    Triple(
      "رسالتنا: إتقان القرآن بالتلقي",
      "نعتني بصحة التلاوة، وتصحيح الأداء، والتدرج في التعلم على أيدي معلمين مؤهلين.",
      "التلاوة الصحيحة أساس كل رحلة قرآنية"
    ),
    Triple(
      "برامج قرآنية ومسارات متكاملة",
      "التلقين وتصحيح الأداء، والحفظ المكثف، وإجازة حفص، والإجازة بالقراءات السبع؛ لكل طالب مسار يناسب مستواه.",
      "تعلم، احفظ، أتقن، ثم انطلق في الإقراء"
    ),
    Triple(
      "الإقراء والإجازة بالسند",
      "بيئة تعليمية منظمة لمتابعة القراءة، والتواصل مع المعلمين، والاستعداد للاختبارات، والسير في طريق أهل القرآن.",
      "خيركم من تعلم القرآن وعلمه"
    )
  )

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaqraahPrimary,
            modifier = Modifier.size(40.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text("۞", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
          }
          Column {
            Text("مقرأة الإمام الجزري", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaqraahPrimary)
            Text("للإقراء والإجازة بالسند", fontSize = 10.sp, color = MaqraahTextMuted)
          }
        }
        OutlinedButton(onClick = onToggleLang, shape = RoundedCornerShape(20.dp)) {
          Text(if (lang == "ar") "English" else "العربية", fontSize = 11.sp, color = MaqraahPrimary)
        }
      }

      Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(vertical = 16.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(
                Brush.linearGradient(listOf(MaqraahPrimaryDark, MaqraahPrimary))
              ),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "۞",
              color = MaqraahGold,
              fontSize = 72.sp,
              fontWeight = FontWeight.Bold
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                  )
                )
            )
            Text(
              text = slides[pageIndex].third,
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp),
              textAlign = TextAlign.Center
            )
          }

          Spacer(modifier = Modifier.height(24.dp))

          Text(
            text = slides[pageIndex].first,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaqraahPrimary,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = slides[pageIndex].second,
            style = MaterialTheme.typography.bodyMedium,
            color = MaqraahTextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
          )
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(bottom = 16.dp)
        ) {
          slides.indices.forEach { idx ->
            Box(
              modifier = Modifier
                .height(7.dp)
                .width(if (idx == pageIndex) 24.dp else 7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (idx == pageIndex) MaqraahPrimary else MaqraahLine)
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          if (pageIndex < slides.size - 1) {
            TextButton(
              onClick = onFinish,
              modifier = Modifier.weight(1f)
            ) {
              Text("تخطٍّ", color = MaqraahTextMuted)
            }
            Button(
              onClick = { pageIndex++ },
              colors = ButtonDefaults.buttonColors(containerColor = MaqraahPrimary),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("التالي")
            }
          } else {
            Button(
              onClick = onFinish,
              colors = ButtonDefaults.buttonColors(containerColor = MaqraahPrimary),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("ابدأ الآن", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
          }
        }
      }
    }
  }
}

@Composable
fun AuthScreen(lang: String, onGoogleSignIn: () -> Unit) {
  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(28.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaqraahPrimary,
        modifier = Modifier.size(88.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text("۞", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "مقرأة الإمام الجزري",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaqraahPrimary,
        textAlign = TextAlign.Center
      )
      Text(
        text = "للإقراء والإجازة بالسند",
        style = MaterialTheme.typography.bodyMedium,
        color = MaqraahTextMuted,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(40.dp))

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "تسجيل الدخول",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaqraahText
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "سجّل الدخول عبر Google للتحقق والانضمام إلى مجتمع المقرأة ومتابعة برامجك.",
            style = MaterialTheme.typography.bodySmall,
            color = MaqraahTextMuted,
            textAlign = TextAlign.Center
          )

          Spacer(modifier = Modifier.height(24.dp))

          Button(
            onClick = onGoogleSignIn,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaqraahLine),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 20.sp)
              Text("المتابعة باستخدام Google", color = MaqraahText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
fun MainAppScaffold(
  lang: String,
  isAdminMode: Boolean,
  onToggleAdminMode: () -> Unit,
  onLogout: () -> Unit
) {
  var selectedTab by remember { mutableStateOf("home") }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, MaqraahLine)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(72.dp)
            .padding(horizontal = 8.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          NavItem(
            icon = Icons.Default.Person,
            label = "حسابي",
            selected = selectedTab == "profile",
            onClick = { selectedTab = "profile" }
          )
          NavItem(
            icon = Icons.Default.Wallet,
            label = "باقاتي",
            selected = selectedTab == "packages",
            onClick = { selectedTab = "packages" }
          )
          Box(
            modifier = Modifier
              .size(56.dp)
              .clip(CircleShape)
              .background(MaqraahPrimary)
              .clickable { selectedTab = "home" },
            contentAlignment = Alignment.Center
          ) {
            Icon(
              Icons.Default.Home,
              contentDescription = "الرئيسية",
              tint = Color.White,
              modifier = Modifier.size(28.dp)
            )
          }
          NavItem(
            icon = Icons.Default.Message,
            label = "المحادثات",
            selected = selectedTab == "chats",
            badgeCount = 2,
            onClick = { selectedTab = "chats" }
          )
          NavItem(
            icon = Icons.Default.AdminPanelSettings,
            label = "الإدارة",
            selected = selectedTab == "admin",
            onClick = { selectedTab = "admin" }
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when (selectedTab) {
        "home" -> HomeScreen(onNavigate = { selectedTab = it })
        "profile" -> ProfileScreen(onLogout = onLogout)
        "packages" -> PackagesScreen()
        "chats" -> ChatsScreen()
        "admin" -> AdminScreen(isAdminMode = isAdminMode, onToggleAdminMode = onToggleAdminMode)
      }
    }
  }
}

@Composable
fun NavItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  selected: Boolean,
  badgeCount: Int = 0,
  onClick: () -> Unit
) {
  val color = if (selected) MaqraahPrimary else MaqraahTextMuted
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box {
      Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
      if (badgeCount > 0) {
        Surface(
          shape = CircleShape,
          color = MaqraahRed,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(16.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(text = badgeCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(text = label, color = color, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
  }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaqraahBackground),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("مرحبًا بك 🌿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaqraahPrimary)
          Text("رحلتك مع القرآن الكريم تبدأ هنا", style = MaterialTheme.typography.bodySmall, color = MaqraahTextMuted)
        }
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaqraahPrimaryLight,
          modifier = Modifier.padding(4.dp)
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End
          ) {
            Text("رصيدك الحالي", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp)
            Text("100 دقيقة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }
        }
      }
    }

    item {
      Text("البرامج التعليمية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaqraahText)
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          ProgramCard(
            title = "التلقين وتصحيح الأداء",
            desc = "تصحيح التلاوة والمخارج بالتلقي",
            icon = Icons.Default.MenuBook,
            modifier = Modifier.weight(1f),
            onClick = {}
          )
          ProgramCard(
            title = "الحفظ المكثف",
            desc = "الحفظ والمراجعة المنتظمة",
            icon = Icons.Default.BookmarkBorder,
            modifier = Modifier.weight(1f),
            onClick = {}
          )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          ProgramCard(
            title = "إجازة حفص",
            desc = "الإقراء وإجازة رواية حفص بالسند",
            icon = Icons.Default.WorkspacePremium,
            modifier = Modifier.weight(1f),
            onClick = {}
          )
          ProgramCard(
            title = "القراءات السبع",
            desc = "مسار متخصص للقراءات السبع",
            icon = Icons.Default.LibraryBooks,
            modifier = Modifier.weight(1f),
            onClick = {}
          )
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaqraahPrimary),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text("مقرأة الإمام الجزري للإقراء والإجازة بالسند", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "تعلم القرآن الكريم بالتلقي والإتقان، وواصل رحلتك العلمية في بيئة تعليمية منظمة.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            lineHeight = 18.sp
          )
        }
      }
    }
  }
}

@Composable
fun ProgramCard(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier.clickable(onClick = onClick)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaqraahBackground,
        modifier = Modifier.size(40.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(icon, contentDescription = null, tint = MaqraahPrimary, modifier = Modifier.size(22.dp))
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaqraahText)
      Spacer(modifier = Modifier.height(4.dp))
      Text(text = desc, fontSize = 10.sp, color = MaqraahTextMuted, lineHeight = 14.sp)
    }
  }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
  val user = Firebase.auth.currentUser
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaqraahBackground),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Surface(
          shape = CircleShape,
          color = MaqraahPrimaryLight,
          modifier = Modifier.size(80.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(user?.displayName?.take(1) ?: "أ", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(user?.displayName ?: "أحمد محمد", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaqraahText)
        Text(user?.email ?: "طالب في مقرأة الإمام الجزري", fontSize = 11.sp, color = MaqraahTextMuted)
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          ProfileInfoRow("البريد الإلكتروني", user?.email ?: "ahmed@example.com")
          Divider(color = MaqraahLine, modifier = Modifier.padding(vertical = 8.dp))
          ProfileInfoRow("الحالة", "نشط ومفعل عبر Google")
          Divider(color = MaqraahLine, modifier = Modifier.padding(vertical = 8.dp))
          ProfileInfoRow("البرنامج الحالي", "التلقين وتصحيح الأداء")
        }
      }
    }

    item {
      Button(
        onClick = onLogout,
        colors = ButtonDefaults.buttonColors(containerColor = MaqraahRed),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("تسجيل الخروج", color = Color.White, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(label, fontSize = 11.sp, color = MaqraahTextMuted, fontWeight = FontWeight.Bold)
    Text(value, fontSize = 11.sp, color = MaqraahText)
  }
}

@Composable
fun PackagesScreen() {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaqraahBackground),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Text("الباقات الدراسية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaqraahText)
    }

    val packagesList = listOf(
      Triple("الباقة الفضية العادية", "100 دقيقة", "500 بر"),
      Triple("الباقة الذهبية", "300 دقيقة", "1,470 بر"),
      Triple("الباقة المتميزة", "500 دقيقة", "2,350 بر"),
      Triple("باقة النخبة", "1,000 دقيقة", "4,500 بر")
    )

    items(packagesList.size) { idx ->
      val pkg = packagesList[idx]
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(pkg.first, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaqraahPrimary)
            Text(pkg.third, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaqraahGold)
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(pkg.second, fontSize = 11.sp, color = MaqraahTextMuted)
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(containerColor = MaqraahPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("اختر الباقة", fontSize = 12.sp)
          }
        }
      }
    }
  }
}

@Composable
fun ChatsScreen() {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaqraahBackground),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    item {
      Text("المحادثات الخاصة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaqraahText)
    }
    item {
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Surface(
            shape = CircleShape,
            color = MaqraahPrimaryLight,
            modifier = Modifier.size(44.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text("م", color = Color.White, fontWeight = FontWeight.Bold)
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("المقرأة الرسمية", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaqraahText)
            Text("السلام عليكم ورحمة الله وبركاته", fontSize = 10.sp, color = MaqraahTextMuted)
          }
          Text("10:40 ص", fontSize = 9.sp, color = MaqraahTextMuted)
        }
      }
    }
  }
}

@Composable
fun AdminScreen(isAdminMode: Boolean, onToggleAdminMode: () -> Unit) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(MaqraahBackground),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("لوحة الإدارة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaqraahText)
        OutlinedButton(onClick = onToggleAdminMode) {
          Text(if (isAdminMode) "معاينة كطالب" else "معاينة كمدير", fontSize = 10.sp)
        }
      }
    }

    if (!isAdminMode) {
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaqraahRed, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("ليس لديك صلاحية الوصول", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaqraahText)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "هذه الصفحة مخصصة للمدير العام والمستخدمين الإداريين المخولين فقط.",
              fontSize = 11.sp,
              color = MaqraahTextMuted,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    } else {
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          AdminStatCard("المستخدمون", "1", modifier = Modifier.weight(1f))
          AdminStatCard("الطلبات", "0", modifier = Modifier.weight(1f))
        }
      }
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          AdminMenuRow("بيانات المستخدمين", Icons.Default.People)
          AdminMenuRow("طلبات التسجيل والشحن", Icons.Default.Assignment)
          AdminMenuRow("المشرفون والمعلمون", Icons.Default.SupervisorAccount)
          AdminMenuRow("المجموعات وإضافة الطلاب", Icons.Default.Group)
          AdminMenuRow("البرامج والباقات", Icons.Default.Category)
          AdminMenuRow("إعدادات التطبيق", Icons.Default.Settings)
        }
      }
    }
  }
}

@Composable
fun AdminStatCard(title: String, value: String, modifier: Modifier = Modifier) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaqraahPrimary)
      Spacer(modifier = Modifier.height(2.dp))
      Text(title, fontSize = 10.sp, color = MaqraahTextMuted)
    }
  }
}

@Composable
fun AdminMenuRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier
      .fillMaxWidth()
      .clickable {}
    ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaqraahPrimary, modifier = Modifier.size(20.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaqraahText)
      }
      Icon(Icons.Default.ChevronLeft, contentDescription = null, tint = MaqraahTextMuted, modifier = Modifier.size(18.dp))
    }
  }
}
