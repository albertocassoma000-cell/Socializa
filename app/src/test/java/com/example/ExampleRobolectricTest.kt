package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SessionPreferences
import com.example.data.model.PostType
import com.example.data.model.PostVisibility
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.repository.MeuPaisRepository
import com.example.ui.MeuPaisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        MeuPaisViewModel.testRepositoryFactory = null
        Dispatchers.resetMain()
    }

    @Test
    fun `app name matches Socializa`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Socializa", appName)
        assertTrue(R.drawable.ic_socializa_round_badge != 0)
    }

    @Test
    fun `clean unauthenticated startup has zero fake posts or users and connects to online ViewModel flows`() = runBlocking(Dispatchers.Default) {
        val server = FakeSupabaseServer()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uid = "firebase-uid-test-123"
        server.registerRawUser(uid, "joao_ao", "João de Angola", "admin")

        MeuPaisViewModel.testRepositoryFactory = { application ->
            MeuPaisRepository(
                context = application.applicationContext,
                preferences = SessionPreferences(application.applicationContext),
                apiOverride = server.createApiService(),
                tokenProviderOverride = { "token-$uid" },
                uidOverride = { uid }
            )
        }

        val vm = MeuPaisViewModel(app)
        withTimeout(5_000) {
            vm.uiState.first { it.isReady }
        }

        // Rotas privadas começam protegidas sem sessão Firebase
        assertFalse(vm.uiState.value.isAuthenticated)
        assertTrue(vm.uiState.value.posts.isEmpty())
        assertTrue(vm.uiState.value.discoveredUsers.isEmpty())
        assertTrue(vm.uiState.value.conversations.isEmpty())

        // Simular sessão autenticada ligada ao servidor simulado
        vm.bindAuthenticatedUserForTest(
            UserProfile(
                id = uid,
                username = "joao_ao",
                fullName = "João de Angola",
                bio = "Criador Meu País",
                role = "admin"
            ),
            loadData = true
        )
        assertTrue(vm.uiState.value.isAuthenticated)
        withTimeout(5_000) {
            vm.uiState.first { it.isAuthenticated && !it.isLoadingFeed }
        }

        // Criar publicação real vinculada ao UID autenticado
        vm.createPost(
            caption = "Bom dia Luanda!",
            postType = PostType.TEXT,
            category = "Cultura",
            location = "Luanda, Angola",
            hashtagsRaw = "#MeuPais #Luanda",
            visibility = PostVisibility.PUBLIC,
            mediaUris = emptyList(),
            publishAlsoToReels = false
        )
        withTimeout(5_000) {
            vm.uiState.first { it.posts.any { p -> p.caption == "Bom dia Luanda!" } }
        }
        val createdPost = vm.uiState.value.posts.first { it.caption == "Bom dia Luanda!" }
        assertEquals(uid, createdPost.authorId)
        assertEquals(0, createdPost.likesCount)

        // Curtir (máximo 1 curtida por utilizador) e comentar publicação
        vm.togglePostLike(createdPost.id)
        withTimeout(5_000) {
            vm.uiState.first { state -> state.posts.firstOrNull { it.id == createdPost.id }?.likesCount == 1 }
        }

        vm.openComments(postId = createdPost.id)
        vm.addComment("Excelente publicação real!")
        withTimeout(5_000) {
            vm.uiState.first { it.activeComments.any { c -> c.body == "Excelente publicação real!" } }
        }

        // Alternar Modo Claro / Escuro
        vm.setThemeMode(ThemeMode.LIGHT)
        withTimeout(5_000) {
            vm.uiState.first { it.themeMode == ThemeMode.LIGHT }
        }
        assertEquals(ThemeMode.LIGHT, vm.uiState.value.themeMode)
    }

    @Test
    fun `AndroidViewModelFactory creates MeuPaisViewModel without throwing`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        MeuPaisViewModel.testRepositoryFactory = null
        val vm = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            .create(MeuPaisViewModel::class.java)
        assertTrue(vm.provinces.isNotEmpty())
    }
}
