package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
import com.example.data.telegram.TelegramConfig
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
            onFinish = { showOnboarding = false }
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
                  Toast.makeText(context, "فشل تسجيل الدخول: $err", Toast.LENGTH_LONG).show()
                },
                onAuthCancelled = {
                  // المستخدم أغلق النافذة، لا نفعل شيئاً
                },
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
                  onComplete = { currentUser = null }
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

// ... (باقي الـ Composables كما هي بدون تغيير: OnboardingScreen, AuthScreen, MainAppScaffold, إلخ)
// لضمان عدم الإطالة، يمكنك الإبقاء على الـ Composables من كودك السابق كما هي تماماً.
// سأرفق فقط الأجزاء الأساسية هنا، لكن في مشروعك لا تحذف الباقي.

@Composable
fun OnboardingScreen(lang: String, onToggleLang: () -> Unit, onFinish: () -> Unit) {
  // ... (نفس الكود السابق)
  var pageIndex by remember { mutableIntStateOf(0) }
  val slides = listOf(
    Triple("مرحبًا بك في مقرأة الإمام الجزري", "منارة قرآنية تُعنى بتعليم كتاب الله بالتلقي والإتقان، وربط الطلاب بأهل القرآن وأسانيد القراءة.", "رحلة علمية تبدأ بآية وتُثمر إتقانًا وسندًا"),
    Triple("رسالتنا: إتقان القرآن بالتلقي", "نعتني بصحة التلاوة، وتصحيح الأداء، والتدرج في التعلم على أيدي معلمين مؤهلين.", "التلاوة الصحيحة أساس كل رحلة قرآنية"),
    Triple("برامج قرآنية ومسارات متكاملة", "التلقين وتصحيح الأداء، والحفظ المكثف، وإجازة حفص، والإجازة بالقراءات السبع؛ لكل طالب مسار يناسب مستواه.", "تعلم، احفظ، أتقن، ثم انطلق في الإقراء"),
    Triple("الإقراء والإجازة بالسند", "بيئة تعليمية منظمة لمتابعة القراءة، والتواصل مع المعلمين، والاستعداد للاختبارات، والسير في طريق أهل القرآن.", "خيركم من تعلم القرآن وعلمه")
  )

  Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(shape = RoundedCornerShape(12.dp), color = MaqraahPrimary, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("۞", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
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
        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp)
      ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(MaqraahPrimaryDark, MaqraahPrimary))),
            contentAlignment = Alignment.Center
          ) {
            Text(text = "۞", color = MaqraahGold, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))))
            Text(
              text = slides[pageIndex].third, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
              modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp), textAlign = TextAlign.Center
            )
          }
          Spacer(modifier = Modifier.height(24.dp))
          Text(text = slides[pageIndex].first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaqraahPrimary, textAlign = TextAlign.Center)
          Spacer(modifier = Modifier.height(12.dp))
          Text(text = slides[pageIndex].second, style = MaterialTheme.typography.bodyMedium, color = MaqraahTextMuted, textAlign = TextAlign.Center, lineHeight = 22.sp)
        }
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 16.dp)) {
          slides.indices.forEach { idx ->
            Box(modifier = Modifier.height(7.dp).width(if (idx == pageIndex) 24.dp else 7.dp).clip(RoundedCornerShape(4.dp)).background(if (idx == pageIndex) MaqraahPrimary else MaqraahLine))
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (pageIndex < slides.size - 1) {
            TextButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("تخطٍّ", color = MaqraahTextMuted) }
            Button(onClick = { pageIndex++ }, colors = ButtonDefaults.buttonColors(containerColor = MaqraahPrimary), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("التالي") }
          } else {
            Button(onClick = onFinish, colors = ButtonDefaults.buttonColors(containerColor = MaqraahPrimary), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
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
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
      verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(shape = RoundedCornerShape(24.dp), color = MaqraahPrimary, modifier = Modifier.size(88.dp)) {
        Box(contentAlignment = Alignment.Center) { Text("۞", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold) }
      }
      Spacer(modifier = Modifier.height(24.dp))
      Text("مقرأة الإمام الجزري", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaqraahPrimary, textAlign = TextAlign.Center)
      Text("للإقراء والإجازة بالسند", style = MaterialTheme.typography.bodyMedium, color = MaqraahTextMuted, textAlign = TextAlign.Center)
      Spacer(modifier = Modifier.height(40.dp))
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("تسجيل الدخول", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaqraahText)
          Spacer(modifier = Modifier.height(8.dp))
          Text("سجّل الدخول عبر Google للتحقق والانضمام إلى مجتمع المقرأة ومتابعة برامجك.", style = MaterialTheme.typography.bodySmall, color = MaqraahTextMuted, textAlign = TextAlign.Center)
          Spacer(modifier = Modifier.height(24.dp))
          Button(
            onClick = onGoogleSignIn,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaqraahLine),
            modifier = Modifier.fillMaxWidth().height(52.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Black, fontSize = 20.sp)
              Text("المتابعة باستخدام Google", color = MaqraahText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
          }
        }
      }
    }
  }
}

// ... (يمكنك إكمال باقي الدوال MainAppScaffold, NavItem, HomeScreen, ProfileScreen, إلخ من كودك السابق كما هي تماماً بدون تغيير)
// ملاحظة: تأكد من عدم حذفها من مشروعك.