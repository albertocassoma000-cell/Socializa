package com.example

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SessionPreferences
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiScreenResponsiveUiTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun setupBoundViewModel(
        authenticated: Boolean = true,
        seedSampleData: Boolean = true
    ): Pair<MeuPaisViewModel, String> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val server = FakeSupabaseServer()
        val uid = "uid-responsive-01"
        val otherUid = "uid-responsive-02"
        server.registerRawUser(uid, "luanda_creator", "Luanda Criador", role = "user")
        server.registerRawUser(otherUid, "benguela_user", "Benguela Social", role = "user")

        var initialPostId = ""
        if (seedSampleData) {
            initialPostId = server.insertRawPost(otherUid, "Bom dia Angola a partir de Benguela!")
        }

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
        runBlocking(Dispatchers.Default) {
            withTimeout(5_000) {
                vm.uiState.first { it.isReady }
            }
            if (authenticated) {
                vm.bindAuthenticatedUserForTest(
                    UserProfile(
                        id = uid,
                        username = "luanda_creator",
                        fullName = "Luanda Criador",
                        bio = "Fotografia & Cultura",
                        province = "Luanda",
                        country = "Angola"
                    ),
                    loadData = true
                )
                withTimeout(5_000) {
                    vm.uiState.first {
                        (!seedSampleData || (it.posts.isNotEmpty() && it.discoveredUsers.isNotEmpty()))
                    }
                }
                vm.startDirectConversation(otherUid)
                withTimeout(5_000) {
                    vm.uiState.first { it.conversations.isNotEmpty() }
                }
                vm.openConversation(null)
            }
        }
        return vm to initialPostId
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `MainActivity boots cleanly with default AndroidViewModelFactory and never throws`() {
        MeuPaisViewModel.testRepositoryFactory = null
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = controller.get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `unauthenticated AuthScreen fits 320dp compact phone with Google email and OTP controls`() {
        val (vm, _) = setupBoundViewModel(authenticated = false, seedSampleData = false)
        composeRule.setContent {
            MeuPaisRoot(viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("socializa_auth_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_tab_SIGN_IN").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_tab_REGISTER").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_tab_FORGOT_PASSWORD").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("google_signin_button").fetchSemanticsNodes().isNotEmpty())
        assertTrue(vm.uiState.value.posts.isEmpty())

        // Alternar para Criar conta e confirmar campo de Nome completo visível sem sobreposição
        composeRule.onNodeWithTag("auth_tab_REGISTER").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auth_fullname_input").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `compact 320dp phone navigates and interacts cleanly across all screens without overlap or crashes`() {
        val (vm, initialPostId) = setupBoundViewModel(authenticated = true, seedSampleData = true)
        composeRule.setContent {
            MeuPaisRoot(viewModel = vm)
        }
        composeRule.waitForIdle()

        // 1. Home Feed: marca Socializa, 3 feeds do algoritmo, ações de topo, compositor rápido, curtida e comentários
        composeRule.onNodeWithText("Socializa").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_mode_FOR_YOU").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_mode_FOLLOWING").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_mode_TRENDING").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_search_button").assertIsDisplayed()
        composeRule.onNodeWithTag("top_notifications_button").assertIsDisplayed()
        composeRule.onNodeWithTag("top_messages_button").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_compose_card").assertIsDisplayed()
        composeRule.onNodeWithTag("feed_post_$initialPostId").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("like_post_$initialPostId").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("comment_post_$initialPostId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("comment_input").assertIsDisplayed().performTextInput("Excelente foto!")
        composeRule.onNodeWithTag("send_comment_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Excelente foto!").assertIsDisplayed()
        composeRule.runOnUiThread { vm.openComments(null, null) }
        composeRule.waitForIdle()

        // 2. Mensagens no ecrã estreito (320dp): filtro, lista, compositor de chat e retorno pelo botão Voltar
        composeRule.onNodeWithTag("top_messages_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("new_chat_button").assertIsDisplayed()
        composeRule.onNodeWithTag("messages_filter_Todas").assertIsDisplayed()

        val firstConvId = vm.uiState.value.conversations.first().id
        composeRule.onNodeWithTag("conversation_$firstConvId").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("chat_message_input").assertIsDisplayed().performTextInput("Olá, tudo bem?")
        composeRule.onNodeWithTag("chat_send_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Olá, tudo bem?").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Voltar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Voltar").performClick()
        composeRule.waitForIdle()

        // 3. Explorar (320dp): Descobrir, Pessoas e Loja + publicar produto real com categoria "Moda"
        composeRule.onNodeWithTag("bottom_tab_explore").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore_mode_discover").assertIsDisplayed()
        composeRule.onNodeWithTag("explore_mode_people").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore_mode_market").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_market_product_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Anunciar no Marketplace").assertIsDisplayed()
        composeRule.onAllNodesWithText("Moda").onLast().performClick()
        composeRule.onNodeWithText("Título do produto").performScrollTo().performTextInput("Camisa Samakaka")
        composeRule.onNodeWithText("Preço em Kwanza (AOA)").performScrollTo().performTextInput("15000")
        composeRule.onNodeWithText("Publicar Anúncio").performScrollTo().performClick()
        composeRule.waitUntil(3_000) {
            vm.uiState.value.products.any { it.title == "Camisa Samakaka" }
        }
        composeRule.waitForIdle()
        assertEquals("Moda", vm.uiState.value.products.firstOrNull { it.title == "Camisa Samakaka" }?.category)

        // 4. Criar Publicação (320dp): barra inferior 5 tipos, música no vídeo e criação de post texto
        composeRule.onNodeWithTag("bottom_tab_create").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("create_type_VIDEO").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pick_video_music_box").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("create_type_TEXT").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("post_caption_input").performScrollTo().performTextInput("Publicação real 320dp")
        composeRule.onNodeWithTag("publish_post_button").assertIsDisplayed().performClick()
        composeRule.waitUntil(3_000) {
            vm.uiState.value.posts.any { it.caption == "Publicação real 320dp" } &&
                composeRule.onAllNodesWithTag("bottom_tab_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("bottom_tab_home").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollToNodeAction()
        ).performScrollToNode(hasText("Publicação real 320dp", substring = true))
        composeRule.onNodeWithText("Publicação real 320dp", substring = true).assertIsDisplayed()

        // 5. Vídeos (320dp)
        composeRule.onNodeWithTag("bottom_tab_videos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("videos_tab_Para ti").assertIsDisplayed()
        composeRule.onNodeWithTag("videos_tab_Seguindo").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // 6. Perfil, Diálogo de Anúncios (botão Fechar) e Configurações (320dp FlowRow)
        composeRule.onNodeWithTag("bottom_tab_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_profile_button").assertIsDisplayed()
        composeRule.onNodeWithTag("open_verification_ads_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("text_ads_verification_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Fechar").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_settings_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_mode_LIGHT").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun `standard 411dp phone renders primary tabs cleanly`() {
        val (vm, initialPostId) = setupBoundViewModel(authenticated = true, seedSampleData = true)
        composeRule.setContent {
            MeuPaisRoot(viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_post_$initialPostId").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_tab_explore").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("explore_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_tab_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("daily_verification_card").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w600dp-h960dp")
    fun `tablet 600dp width renders adaptive centered frame across screens cleanly`() {
        val (vm, initialPostId) = setupBoundViewModel(authenticated = true, seedSampleData = true)
        composeRule.setContent {
            MeuPaisRoot(viewModel = vm)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("feed_post_$initialPostId").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_tab_explore").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("category_Música").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_tab_videos").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("videos_tab_Para ti").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_tab_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_profile_button").assertIsDisplayed()
    }
}
