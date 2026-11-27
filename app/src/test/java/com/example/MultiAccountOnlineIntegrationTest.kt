package com.example

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SessionPreferences
import com.example.data.model.CommunityGroup
import com.example.data.model.DeliveryStatus
import com.example.data.model.FeedMode
import com.example.data.model.MarketplaceProduct
import com.example.data.model.MessageType
import com.example.data.model.NotificationEventType
import com.example.data.model.OtpAuthStep
import com.example.data.model.PostType
import com.example.data.model.PostVisibility
import com.example.data.model.UserProfile
import com.example.data.model.VideoReel
import com.example.data.model.VideoTelemetry
import com.example.data.remote.EnsureFirebaseProfileRequest
import com.example.data.remote.RateLimitExceededException
import com.example.data.remote.SupabaseApiService
import com.example.data.remote.TokenBucketRateLimiter
import com.example.data.repository.AuthResult
import com.example.data.repository.MeuPaisRepository
import com.example.domain.algorithm.SocializaAlgorithmEngine
import com.example.ui.MeuPaisViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiAccountOnlineIntegrationTest {

    private fun newRepo(server: FakeSupabaseServer, uid: String): MeuPaisRepository {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return MeuPaisRepository(
            context = app.applicationContext,
            preferences = SessionPreferences(app.applicationContext),
            apiOverride = server.createApiService(),
            tokenProviderOverride = { "token-$uid" },
            uidOverride = { uid }
        )
    }

    @Test
    fun `two real accounts interact end to end across posts comments replies likes chat notifications and blocks`() = runBlocking {
        val server = FakeSupabaseServer()
        val api = server.createApiService()

        val uidA = "firebase-uid-conta-a"
        val uidB = "firebase-uid-conta-b"

        // 1. Registar e sincronizar 2 contas distintas via RPC ensure_firebase_profile (zero contas fictícias)
        val profileADto = api.ensureFirebaseProfile(
            apiKey = "test-key",
            firebaseBearer = "Bearer token-$uidA",
            body = EnsureFirebaseProfileRequest("Ana de Luanda", null, "Luanda", "Angola")
        ).body()!!
        val profileBDto = api.ensureFirebaseProfile(
            apiKey = "test-key",
            firebaseBearer = "Bearer token-$uidB",
            body = EnsureFirebaseProfileRequest("Carlos de Benguela", null, "Benguela", "Angola")
        ).body()!!

        val repoA = newRepo(server, uidA)
        val repoB = newRepo(server, uidB)

        val userA = UserProfile(id = uidA, username = profileADto.username, fullName = "Ana de Luanda", province = "Luanda")
        val userB = UserProfile(id = uidB, username = profileBDto.username, fullName = "Carlos de Benguela", province = "Benguela")

        // Estado inicial tem ZERO publicações falsas
        assertTrue(repoA.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow().isEmpty())
        assertTrue(repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow().isEmpty())

        // 2. Conta A envia uma fotografia real (ficheiro temporário local comprimido + upload Storage CDN) e cria publicação
        val app = ApplicationProvider.getApplicationContext<Application>()
        val tempPhoto = File(app.cacheDir, "test_upload_photo.png").apply {
            val bmp = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.rgb(244, 108, 22))
            outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        val createdPost = repoA.createPost(
            caption = "Pôr do sol na Marginal de Luanda",
            postType = PostType.PHOTO,
            category = "Viagens",
            location = "Luanda, Angola",
            hashtags = listOf("#Luanda", "#MeuPais"),
            visibility = PostVisibility.PUBLIC,
            mediaUris = listOf(Uri.fromFile(tempPhoto)),
            publishAlsoToReels = false,
            currentUser = userA
        ).getOrThrow()

        assertEquals(uidA, createdPost.authorId)
        assertEquals(1, createdPost.mediaUrls.size)
        assertTrue(createdPost.mediaUrls.first().contains("/storage/v1/object/public/media/"))

        // 3. Conta B atualiza o feed, descobre Conta A e vê a publicação real
        val searchedByB = repoB.searchUsers("Ana").getOrThrow()
        assertEquals(1, searchedByB.size)
        assertEquals(uidA, searchedByB.first().id)

        val feedForB = repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow()
        assertEquals(1, feedForB.size)
        assertEquals(createdPost.id, feedForB.first().id)
        assertEquals("Pôr do sol na Marginal de Luanda", feedForB.first().caption)

        // Conta B regista visualização
        repoB.recordPostView(createdPost.id)

        // 4. Conta B segue Conta A e curte a publicação (apenas 1 curtida permitida por utilizador)
        assertTrue(repoB.toggleFollowUser(uidA, currentlyFollowing = false, actorName = userB.fullName).getOrThrow())
        assertTrue(repoB.togglePostLike(feedForB.first(), actorName = userB.fullName).getOrThrow())

        val feedAfterLike = repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow()
        assertEquals(1, feedAfterLike.first().likesCount)
        assertEquals(1, feedAfterLike.first().viewsCount)
        assertTrue(feedAfterLike.first().isLiked)

        // Tentar curtir novamente pelo mesmo utilizador na API devolve 409 e mantém exatamente 1 curtida
        val dupLikeRes = api.likePost("test-key", "Bearer token-$uidB", com.example.data.remote.SupabaseLikeRecordDto(userId = uidB, postId = createdPost.id))
        assertEquals(409, dupLikeRes.code())
        assertEquals(1, repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow().first().likesCount)

        // 5. Conta B comenta na publicação de A; Conta A lê e responde ao comentário; Conta B curte a resposta
        val commentFromB = repoB.addComment(
            postId = createdPost.id,
            body = "Fotografia espetacular, Ana!",
            targetOwnerId = uidA,
            actor = userB
        ).getOrThrow()

        val commentsSeenByA = repoA.fetchComments(postId = createdPost.id).getOrThrow()
        assertEquals(1, commentsSeenByA.size)
        assertEquals(uidB, commentsSeenByA.first().authorId)

        val replyFromA = repoA.addComment(
            postId = createdPost.id,
            body = "Muito obrigada Carlos!",
            parentCommentId = commentFromB.id,
            targetOwnerId = uidA,
            parentCommentAuthorId = uidB,
            actor = userA
        ).getOrThrow()
        assertEquals(commentFromB.id, replyFromA.parentId)

        assertTrue(repoB.toggleCommentLike(replyFromA.id, currentlyLiked = false).getOrThrow())
        val threadSeenByB = repoB.fetchComments(postId = createdPost.id).getOrThrow()
        assertEquals(2, threadSeenByB.size)
        assertEquals(1, threadSeenByB.first { it.id == replyFromA.id }.likesCount)

        // 6. Conta A edita a sua publicação e verifica que Conta B vê a edição
        repoA.editPost(createdPost.id, "Pôr do sol atualizado na Baía de Luanda", listOf("#Luanda"), "Luanda, Angola", PostVisibility.PUBLIC).getOrThrow()
        assertEquals(
            "Pôr do sol atualizado na Baía de Luanda",
            repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow().first().caption
        )

        // 7. Mensagens reais entre Conta A e Conta B + estado de entrega/leitura
        val convId = repoA.startDirectConversation(uidB).getOrThrow()
        val msgFromA = repoA.sendMessage(
            conversationId = convId,
            body = "Olá Carlos, tudo bem?",
            type = MessageType.TEXT,
            recipientUserId = uidB,
            senderName = userA.fullName
        ).getOrThrow()
        assertEquals(DeliveryStatus.SENT, msgFromA.deliveryStatus)

        // Conta B abre a conversa (marca mensagens como lidas no backend) e responde
        val msgsSeenByB = repoB.fetchMessages(convId).getOrThrow()
        assertEquals(1, msgsSeenByB.size)
        assertEquals("Olá Carlos, tudo bem?", msgsSeenByB.first().body)

        val msgsSeenByA = repoA.fetchMessages(convId).getOrThrow()
        assertEquals(DeliveryStatus.READ, msgsSeenByA.first { it.id == msgFromA.id }.deliveryStatus)

        // 8. Notificações reais geradas pelas ações entre contas
        val notificationsA = repoA.fetchNotifications().getOrThrow()
        assertTrue(notificationsA.any { it.type == NotificationEventType.FOLLOW })
        assertTrue(notificationsA.any { it.type == NotificationEventType.LIKE })
        assertTrue(notificationsA.any { it.type == NotificationEventType.COMMENT })

        val notificationsB = repoB.fetchNotifications().getOrThrow()
        assertTrue(notificationsB.any { it.type == NotificationEventType.COMMENT_REPLY })
        assertTrue(notificationsB.any { it.type == NotificationEventType.MESSAGE })

        // 9. Bloqueio entre utilizadores: Conta A bloqueia Conta B
        repoA.blockUser(uidB).getOrThrow()
        // Feed de Conta B deixa de ver publicações de Conta A
        val feedAfterBlock = repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow()
        assertTrue(feedAfterBlock.isEmpty())
        // Nova tentativa de iniciar conversa direta é rejeitada por bloqueio
        assertTrue(repoB.startDirectConversation(uidA).isFailure)

        // 10. Conta A pode eliminar a sua própria publicação
        repoA.deletePost(createdPost.id).getOrThrow()
        assertTrue(repoA.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow().isEmpty())
    }

    @Test
    fun `pagination concurrency and rate limiter enforce scalability and anti-spam rules`() = runBlocking {
        val server = FakeSupabaseServer()
        val api = server.createApiService()
        val uid = "firebase-uid-scale"
        api.ensureFirebaseProfile("test-key", "Bearer token-$uid", EnsureFirebaseProfileRequest("Criador Escala", null, "Luanda", "Angola"))
        val repo = newRepo(server, uid)
        val user = UserProfile(id = uid, username = "escala", fullName = "Criador Escala")

        // Criar 20 publicações para validar paginação progressiva (page 0 size 15 + page 1 size 5)
        repeat(20) { index ->
            server.insertRawPost(uid, "Publicação #$index", visibility = "public")
        }

        val page0 = repo.fetchFeedPosts(FeedMode.TRENDING, page = 0, pageSize = 15).getOrThrow()
        val page1 = repo.fetchFeedPosts(FeedMode.TRENDING, page = 1, pageSize = 15).getOrThrow()
        assertEquals(15, page0.size)
        assertEquals(5, page1.size)
        assertTrue(page0.map { it.id }.intersect(page1.map { it.id }.toSet()).isEmpty())

        // Concorrência: múltiplos pedidos em paralelo sem falhas
        val concurrentReads = (0 until 5).map {
            async { repo.fetchFeedPosts(FeedMode.FOR_YOU, page = 0, pageSize = 10).getOrThrow() }
        }.awaitAll()
        assertTrue(concurrentReads.all { it.size == 10 })

        // Anti-spam Rate Limiter
        val limiter = TokenBucketRateLimiter()
        repeat(12) { idx ->
            limiter.checkAndRecord("create_post", maxPerMinute = 12, nowMs = 1_000_000L + idx)
        }
        var threwRateLimit = false
        try {
            limiter.checkAndRecord("create_post", maxPerMinute = 12, nowMs = 1_000_050L)
        } catch (_: RateLimitExceededException) {
            threwRateLimit = true
        }
        assertTrue(threwRateLimit)
    }

    @Test
    fun `http 401 automatically recovers via refreshed token and firebase-session exchange`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uid = "firebase-uid-401-recover"

        // Cenário A: primeiro token expirado recebe HTTP 401; refresh devolve token válido
        val serverA = FakeSupabaseServer().apply {
            registerRawUser(uid, "ana_recover", "Ana Recuperação")
            insertRawPost(uid, "Post após renovação")
            rejectedTokens.add("stale-token")
        }
        val calls = AtomicInteger(0)
        val repoA = MeuPaisRepository(
            context = app.applicationContext,
            preferences = SessionPreferences(app.applicationContext),
            apiOverride = serverA.createApiService(),
            tokenProviderOverride = {
                if (calls.getAndIncrement() == 0) "stale-token" else "token-$uid"
            },
            uidOverride = { uid }
        )
        val feedA = repoA.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow()
        assertEquals(1, feedA.size)
        assertNull(repoA.getBackendStatus().supabaseAuthDiagnostic)

        // Cenário B: Firebase ID Token sem claim role="authenticated" recebe 401 no PostgREST,
        // mas a Edge Function firebase-session emite JWT Supabase válido e tanto feed como createPost funcionam
        val rawFirebaseJwt = fakeJwt(
            """{"sub":"$uid","aud":"gen-lang-client-0990149874","exp":4102444800}"""
        )
        val serverB = FakeSupabaseServer().apply {
            registerRawUser(uid, "carlos_exchange", "Carlos Exchange")
            rejectedTokens.add(rawFirebaseJwt)
            exchangeEnabled = true
            exchangeTargetUid = uid
        }
        val repoB = MeuPaisRepository(
            context = app.applicationContext,
            preferences = SessionPreferences(app.applicationContext),
            apiOverride = serverB.createApiService(),
            tokenProviderOverride = { rawFirebaseJwt },
            uidOverride = { uid }
        )
        val userB = UserProfile(id = uid, username = "carlos_exchange", fullName = "Carlos Exchange")
        val created = repoB.createPost(
            caption = "Publicação após troca de sessão!",
            postType = PostType.TEXT,
            category = null,
            location = "Luanda",
            hashtags = listOf("#MeuPais"),
            visibility = PostVisibility.PUBLIC,
            mediaUris = emptyList(),
            publishAlsoToReels = false,
            currentUser = userB
        ).getOrThrow()
        assertEquals("Publicação após troca de sessão!", created.caption)
        val feedB = repoB.fetchFeedPosts(FeedMode.TRENDING, page = 0).getOrThrow()
        assertEquals(1, feedB.size)
        assertNull(repoB.getBackendStatus().supabaseAuthDiagnostic)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `persistent 401 populates actionable jwt role diagnostic and viewmodel avoids duplicate startup feed fetches`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uid = "firebase-uid-diag"
        val rawJwtWithoutRole = fakeJwt(
            """{"sub":"$uid","aud":"gen-lang-client-0990149874","exp":4102444800}"""
        )
        val server = FakeSupabaseServer().apply {
            registerRawUser(uid, "diag_user", "Diagnóstico")
            rejectedTokens.add(rawJwtWithoutRole)
            exchangeEnabled = false // Simula Edge Function não publicada (404) + PostgREST 401
        }
        val repo = MeuPaisRepository(
            context = app.applicationContext,
            preferences = SessionPreferences(app.applicationContext),
            apiOverride = server.createApiService(),
            tokenProviderOverride = { rawJwtWithoutRole },
            uidOverride = { uid }
        )

        val feedResult = repo.fetchFeedPosts(FeedMode.TRENDING, page = 0)
        assertTrue(feedResult.isFailure)
        val errMsg = feedResult.exceptionOrNull()?.message.orEmpty()
        assertTrue("Erro HTTP 401 devia preservar detalhe do servidor, mas foi: $errMsg", errMsg.contains("Invalid JWT or missing role claim"))
        val diagnostic = repo.getBackendStatus().supabaseAuthDiagnostic
        assertNotNull(diagnostic)
        assertTrue(diagnostic!!.contains("role=\"authenticated\""))
        assertTrue(diagnostic.contains("firebase-session"))

        // Validar que resetAuthRecoveryForRetry limpa estado 404 em cache e permite recuperar após deploy da Edge Function
        server.exchangeEnabled = true
        server.exchangeTargetUid = uid
        repo.resetAuthRecoveryForRetry()
        assertFalse(repo.hasAuthRejection())
        val recoveredFeed = repo.fetchFeedPosts(FeedMode.TRENDING, page = 0)
        assertTrue(recoveredFeed.isSuccess)
        assertNull(repo.getBackendStatus().supabaseAuthDiagnostic)

        // Validar que ViewModel executa exatamente 1 fetch_feed no carregamento inicial (sem corrida duplicada)
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            val cleanServer = FakeSupabaseServer().apply {
                registerRawUser(uid, "diag_user", "Diagnóstico")
                insertRawPost(uid, "Post único inicial")
            }
            MeuPaisViewModel.testRepositoryFactory = { application ->
                MeuPaisRepository(
                    context = application.applicationContext,
                    preferences = SessionPreferences(application.applicationContext),
                    apiOverride = cleanServer.createApiService(),
                    tokenProviderOverride = { "token-$uid" },
                    uidOverride = { uid }
                )
            }
            val vm = MeuPaisViewModel(app)
            withTimeout(5_000) { vm.uiState.first { it.isReady } }
            vm.bindAuthenticatedUserForTest(
                UserProfile(id = uid, username = "diag_user", fullName = "Diagnóstico"),
                loadData = true
            )
            withTimeout(5_000) { vm.uiState.first { it.posts.isNotEmpty() } }
            assertEquals(1, cleanServer.feedGetCount.get())
        } finally {
            MeuPaisViewModel.testRepositoryFactory = null
            Dispatchers.resetMain()
        }
    }

    private fun fakeJwt(payloadJson: String): String {
        val header = Base64.encodeToString(
            """{"alg":"RS256","typ":"JWT"}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val payload = Base64.encodeToString(
            payloadJson.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$header.$payload.fakesig"
    }

    @Test
    fun `five text ads grant 24h verification badge that expires automatically and video posts persist phone music`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val uid = "firebase-uid-ads-music"
        val server = FakeSupabaseServer().apply {
            registerRawUser(uid, "creator_angola", "Criador Angola")
        }
        val repo = newRepo(server, uid)
        val initialUser = UserProfile(id = uid, username = "creator_angola", fullName = "Criador Angola")

        // 1. Menos de 5 anúncios deve ser recusado
        assertFalse(initialUser.hasActiveVerifiedBadge)
        assertTrue(repo.claimDailyVerifiedBadge(adsWatched = 4, currentUser = initialUser).isFailure)

        // 2. Após 5 anúncios de texto, o selo fica ativo durante exatamente ~24 horas
        val claimedProfile = repo.claimDailyVerifiedBadge(adsWatched = 5, currentUser = initialUser).getOrThrow()
        assertTrue(claimedProfile.hasActiveVerifiedBadge)
        assertTrue(claimedProfile.verifiedUntilMs > System.currentTimeMillis())
        // Simula passagem de 24h + 1 segundo -> o selo desaparece automaticamente
        assertFalse(claimedProfile.isBadgeActive(nowMs = claimedProfile.verifiedUntilMs + 1_000L))

        // 3. Publicar vídeo com música do telemóvel persiste tanto no Feed como nos Vídeos (Reels)
        val fakeVideo = File(app.cacheDir, "test_video_clip.mp4").apply {
            writeBytes(byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70))
        }
        val fakeAudio = File(app.cacheDir, "kuduro_mix.mp3").apply {
            writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00))
        }

        val createdVideoPost = repo.createPost(
            caption = "Dança na Marginal",
            postType = PostType.VIDEO,
            category = "Música",
            location = "Luanda, Angola",
            hashtags = listOf("#Angola", "#Música"),
            visibility = PostVisibility.PUBLIC,
            mediaUris = listOf(Uri.fromFile(fakeVideo)),
            publishAlsoToReels = true,
            currentUser = claimedProfile,
            audioUri = Uri.fromFile(fakeAudio),
            audioTitle = "Kuduro Mix"
        ).getOrThrow()

        assertEquals(PostType.VIDEO, createdVideoPost.postType)
        assertTrue(createdVideoPost.authorVerified)
        assertEquals("Kuduro Mix", createdVideoPost.musicTitle)
        assertNotNull(createdVideoPost.musicUrl)
        assertTrue(createdVideoPost.musicUrl!!.contains("/storage/v1/object/public/media/"))

        val feedPosts = repo.fetchFeedPosts(FeedMode.FOR_YOU, page = 0).getOrThrow()
        assertEquals(1, feedPosts.size)
        assertNotNull(feedPosts.first().musicUrl)
        assertTrue(feedPosts.first().authorVerified)

        val reels = repo.fetchVideos(page = 0).getOrThrow()
        assertEquals(1, reels.size)
        assertEquals("Kuduro Mix", reels.first().musicTitle)
        assertEquals(createdVideoPost.musicUrl, reels.first().musicUrl)
        assertTrue(reels.first().isVerified)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `email password registration 6-digit otp 5-minute expiry resend and link-free password recovery work end to end`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val server = FakeSupabaseServer()
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        try {
            MeuPaisViewModel.testRepositoryFactory = { application ->
                MeuPaisRepository(
                    context = application.applicationContext,
                    preferences = SessionPreferences(application.applicationContext),
                    apiOverride = server.createApiService(),
                    tokenProviderOverride = {
                        server.latestSignedInUid?.let { "token-$it" }
                    },
                    uidOverride = { server.latestSignedInUid },
                    emailSignInOverride = { email, password ->
                        val verified = server.verifyEmailPasswordLogin(email, password)
                        if (verified != null) {
                            AuthResult.Success(verified, "Bem-vindo ao Socializa, @${verified.username}!")
                        } else {
                            AuthResult.Error("E-mail ou senha incorretos.")
                        }
                    }
                )
            }

            val vm = MeuPaisViewModel(app)
            withTimeout(5_000) { vm.uiState.first { it.isReady } }

            // 1. Iniciar registo com Nome, E-mail e Senha -> recebe OTP de 6 dígitos com TTL de 5 min
            vm.startEmailRegistration("Nadia Socializa", "nadia@socializa.ao", "segredo123")
            withTimeout(5_000) { vm.uiState.first { it.otpStep == OtpAuthStep.REGISTER_CODE } }
            val firstOtp = server.lastOtpFor("nadia@socializa.ao", "register")
            assertNotNull(firstOtp)
            assertEquals(6, firstOtp!!.length)
            assertTrue(vm.uiState.value.otpRemainingSeconds in 290..300)

            // 2. Código errado rejeita e mantém utilizador no ecrã OTP
            vm.confirmRegistrationOtp("000000")
            withTimeout(5_000) { vm.uiState.first { it.authErrorMessage != null && !it.isLoadingAuth } }
            assertFalse(vm.uiState.value.isAuthenticated)

            // 3. Simular expiração do código -> confirmar após 5 min é rejeitado
            server.expireOtp("nadia@socializa.ao", "register")
            vm.confirmRegistrationOtp(firstOtp)
            withTimeout(5_000) { vm.uiState.first { it.authErrorMessage != null && !it.isLoadingAuth } }
            assertFalse(vm.uiState.value.isAuthenticated)

            // 4. Reenviar código gera novo OTP válido e confirma autenticação
            server.sendRawOtpForTest("nadia@socializa.ao", "register")
            val validOtp = server.lastOtpFor("nadia@socializa.ao", "register")!!
            vm.confirmRegistrationOtp(validOtp)
            withTimeout(5_000) { vm.uiState.first { it.isAuthenticated } }
            assertEquals("Nadia Socializa", vm.uiState.value.currentUser?.fullName)

            // 5. Terminar sessão e testar recuperação de senha apenas por código de 6 dígitos (sem links)
            vm.signOut()
            withTimeout(5_000) { vm.uiState.first { !it.isAuthenticated } }

            vm.startPasswordReset("nadia@socializa.ao")
            withTimeout(5_000) { vm.uiState.first { it.otpStep == OtpAuthStep.RESET_CODE } }
            val resetOtp = server.lastOtpFor("nadia@socializa.ao", "reset")!!
            vm.confirmPasswordReset(resetOtp, "novaSenha456")
            withTimeout(5_000) { vm.uiState.first { it.otpStep == OtpAuthStep.NONE && !it.isLoadingAuth } }

            // 6. Entrar com a nova senha funciona
            vm.signInWithEmail("nadia@socializa.ao", "novaSenha456")
            withTimeout(5_000) { vm.uiState.first { it.isAuthenticated } }
            assertEquals("Nadia Socializa", vm.uiState.value.currentUser?.fullName)
        } finally {
            MeuPaisViewModel.testRepositoryFactory = null
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `socializa algorithms rank feeds videos people trends unified search and block spam`() {
        val me = UserProfile(id = "u_me", username = "me", fullName = "Utilizador Atual", province = "Luanda")
        val alice = UserProfile(id = "u_alice", username = "alice", fullName = "Alice Semba", province = "Luanda", followersCount = 120, postsCount = 5, isVerified = true)
        val bob = UserProfile(id = "u_bob", username = "bob", fullName = "Bob Huambo", province = "Huambo", followersCount = 15, postsCount = 1)
        val followed = UserProfile(id = "u_fol", username = "fol", fullName = "Amigo Seguido", province = "Benguela", isFollowedByMe = true)

        val pMusic = com.example.data.model.FeedPost(
            id = "p1", authorId = "u_alice", authorName = alice.fullName, authorUsername = alice.username,
            postType = PostType.PHOTO, caption = "Festival de música em Luanda", hashtags = listOf("#Música", "#Luanda"),
            category = "Música", locationLabel = "Luanda", likesCount = 25, commentsCount = 8, sharesCount = 5
        )
        val pOther = com.example.data.model.FeedPost(
            id = "p2", authorId = "u_bob", authorName = bob.fullName, authorUsername = bob.username,
            postType = PostType.TEXT, caption = "Tarde tranquila", hashtags = listOf("#Huambo"),
            locationLabel = "Huambo", likesCount = 1
        )
        val pFollowed = com.example.data.model.FeedPost(
            id = "p3", authorId = "u_fol", authorName = followed.fullName, authorUsername = followed.username,
            postType = PostType.TEXT, caption = "Novidades de Benguela", isAuthorFollowed = true, likesCount = 3
        )
        val pExtra1 = pMusic.copy(id = "p_x1", caption = "Post extra 1", likesCount = 24)
        val pExtra2 = pMusic.copy(id = "p_x2", caption = "Post extra 2", likesCount = 23)

        // 1. Feed FOR_YOU prioriza interesses + proximidade + interação e aplica anti-repetição (-20)
        val forYouRanked = SocializaAlgorithmEngine.rankFeedPosts(
            posts = listOf(pOther, pMusic, pExtra1, pExtra2, pFollowed),
            mode = FeedMode.FOR_YOU,
            currentUser = me,
            interestWeights = mapOf("#música" to 8.0)
        )
        assertEquals("p1", forYouRanked.first().id)

        // 2. Feed FOLLOWING apenas inclui seguidos/próprio; TRENDING ordena por viralidade
        val followingFeed = SocializaAlgorithmEngine.rankFeedPosts(listOf(pOther, pMusic, pFollowed), FeedMode.FOLLOWING, me)
        assertEquals(listOf("p3"), followingFeed.map { it.id })
        val trendingFeed = SocializaAlgorithmEngine.rankFeedPosts(listOf(pOther, pMusic, pFollowed), FeedMode.TRENDING, me)
        assertEquals("p1", trendingFeed.first().id)

        // 3. Recomendação de vídeos usa tempo assistido, conclusão e replay
        val vLow = VideoReel(id = "v1", authorId = "u_bob", authorName = bob.fullName, authorUsername = bob.username, streamUrl = "https://x/1.mp4", caption = "Clip curto")
        val vHigh = VideoReel(id = "v2", authorId = "u_alice", authorName = alice.fullName, authorUsername = alice.username, streamUrl = "https://x/2.mp4", caption = "Kuduro ao vivo", hashtags = listOf("#Música"))
        val rankedVideos = SocializaAlgorithmEngine.rankRecommendedVideos(
            reels = listOf(vLow, vHigh),
            interestWeights = mapOf("#música" to 5.0),
            telemetryByVideo = mapOf("v2" to VideoTelemetry(watchSeconds = 22, completed = true, replayed = true))
        )
        assertEquals("v2", rankedVideos.first().id)

        // 4. Sugestões de pessoas excluem já seguidos e priorizam província + interesses + popularidade
        val suggested = SocializaAlgorithmEngine.suggestPeople(
            candidates = listOf(bob, followed, alice),
            currentUser = me,
            posts = listOf(pMusic, pOther),
            interestWeights = mapOf("#música" to 5.0)
        )
        assertFalse(suggested.any { it.id == "u_fol" })
        assertEquals("u_alice", suggested.first().id)

        // 5. Tendências e Pesquisa unificada (Pessoas, Publicações, Vídeos, Grupos, Lojas/Páginas, Hashtags)
        val trends = SocializaAlgorithmEngine.computeTrendingTopics(listOf(pMusic, pOther), listOf(vHigh))
        assertEquals("#Música", trends.first().tag)
        val unified = SocializaAlgorithmEngine.searchUnified(
            query = "Semba",
            people = listOf(alice, bob),
            posts = listOf(pMusic, pOther),
            reels = listOf(vHigh),
            groups = listOf(CommunityGroup("g1", "u_alice", "Clube Semba Luanda", "Luanda", "Música", 50, false)),
            products = listOf(MarketplaceProduct("pr1", "u_alice", "Loja Semba", "Vinil Semba", 5000L, "Música", "Luanda", null, "Disco")),
            trendingTopics = trends
        )
        assertTrue(unified.people.isNotEmpty())
        assertTrue(unified.groups.isNotEmpty())
        assertTrue(unified.pages.isNotEmpty())

        // 6. Segurança e Anti-spam
        assertFalse(SocializaAlgorithmEngine.evaluateContentSafety("aaaaaaaaaaaaaaaaaaaaaaaaaaaa").isAllowed)
        assertFalse(SocializaAlgorithmEngine.evaluateContentSafety("Duplicado exatamente igual agora", listOf("Duplicado exatamente igual agora")).isAllowed)
        assertTrue(SocializaAlgorithmEngine.evaluateContentSafety("Bom dia comunidade Socializa!").isAllowed)
    }
}

/**
 * Servidor HTTP simulado em memória (OkHttp Interceptor) exclusivo para testes automatizados multi-conta.
 * Implementa as regras de autenticação por UID, bloqueios, 1 curtida por utilizador, notificações e Storage CDN.
 */
class FakeSupabaseServer : Interceptor {
    private val profiles = ConcurrentHashMap<String, JSONObject>()
    private val posts = mutableListOf<JSONObject>()
    private val videos = mutableListOf<JSONObject>()
    private val media = mutableListOf<JSONObject>()
    private val likes = mutableSetOf<Pair<String, String>>() // (uid, postId)
    private val savedPosts = mutableSetOf<Pair<String, String>>()
    private val postViews = mutableSetOf<Pair<String, String>>()
    private val comments = mutableListOf<JSONObject>()
    private val commentLikes = mutableSetOf<Pair<String, String>>()
    private val follows = mutableSetOf<Pair<String, String>>() // (follower, following)
    private val blocks = mutableSetOf<Pair<String, String>>() // (blocker, blocked)
    private val conversations = mutableListOf<JSONObject>()
    private val conversationMembers = mutableListOf<Pair<String, String>>() // (convId, uid)
    private val messages = mutableListOf<JSONObject>()
    private val notifications = mutableListOf<JSONObject>()
    private val products = mutableListOf<JSONObject>()
    private val emailPasswords = ConcurrentHashMap<String, String>()
    private val emailUids = ConcurrentHashMap<String, String>()
    private val otpRecords = ConcurrentHashMap<String, OtpRecord>()
    private var seq = 1L
    val rejectedTokens: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val feedGetCount = AtomicInteger(0)
    var exchangeEnabled: Boolean = false
    var exchangeTargetUid: String = ""
    @Volatile var latestSignedInUid: String? = null

    data class OtpRecord(
        val code: String,
        val fullName: String,
        var expiresAtMs: Long
    )

    fun lastOtpFor(email: String, purpose: String): String? =
        otpRecords["${email.lowercase()}:$purpose"]?.code

    fun expireOtp(email: String, purpose: String) {
        otpRecords["${email.lowercase()}:$purpose"]?.let { it.expiresAtMs = System.currentTimeMillis() - 10_000L }
    }

    fun sendRawOtpForTest(email: String, purpose: String) {
        val key = "${email.lowercase()}:$purpose"
        val prev = otpRecords[key]
        otpRecords[key] = OtpRecord(
            code = "654321",
            fullName = prev?.fullName ?: "Utilizador",
            expiresAtMs = System.currentTimeMillis() + 300_000L
        )
    }

    fun verifyEmailPasswordLogin(email: String, password: String): UserProfile? {
        val clean = email.trim().lowercase()
        val storedPass = emailPasswords[clean] ?: return null
        if (storedPass != password) return null
        val uid = emailUids[clean] ?: return null
        latestSignedInUid = uid
        val p = profiles[uid] ?: return null
        return UserProfile(
            id = uid,
            username = p.optString("username"),
            fullName = p.optString("full_name"),
            province = p.optString("province", "Luanda")
        )
    }

    @Synchronized
    private fun nextId(prefix: String): String = "$prefix-${seq++}-${UUID.randomUUID().toString().take(6)}"

    fun createApiService(): SupabaseApiService {
        val client = OkHttpClient.Builder().addInterceptor(this).build()
        return SupabaseApiService.create("https://mock.supabase.local/", client)!!
    }

    @Synchronized
    fun registerRawUser(uid: String, username: String, fullName: String, role: String = "user") {
        profiles[uid] = JSONObject()
            .put("id", uid)
            .put("username", username)
            .put("full_name", fullName)
            .put("bio", "")
            .put("country", "Angola")
            .put("province", "Luanda")
            .put("is_private", false)
            .put("is_verified", false)
            .put("role", role)
            .put("is_suspended", false)
            .put("followers_count", 0)
            .put("following_count", 0)
            .put("posts_count", 0)
            .put("is_online", true)
    }

    @Synchronized
    fun insertRawPost(authorId: String, caption: String, visibility: String = "public"): String {
        val id = nextId("post")
        posts.add(
            0,
            JSONObject()
                .put("id", id)
                .put("author_id", authorId)
                .put("post_type", "text")
                .put("caption", caption)
                .put("hashtags", JSONArray())
                .put("location_label", "Luanda, Angola")
                .put("visibility", visibility)
                .put("likes_count", 0)
                .put("comments_count", 0)
                .put("shares_count", 0)
                .put("views_count", 0)
                .put("created_at", "2026-11-22T12:00:${String.format("%02d", (seq % 59).toInt())}Z")
        )
        return id
    }

    private fun rawBearerToken(request: okhttp3.Request): String =
        request.header("Authorization").orEmpty().removePrefix("Bearer ").trim()

    private fun extractUid(request: okhttp3.Request): String {
        val raw = rawBearerToken(request)
        if (raw.startsWith("exchanged-")) return raw.removePrefix("exchanged-").trim()
        return raw.removePrefix("token-").trim()
    }

    private fun readBody(request: okhttp3.Request): String {
        val body = request.body ?: return ""
        val buf = Buffer()
        body.writeTo(buf)
        return buf.readUtf8()
    }

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val rawToken = rawBearerToken(request)
        val uid = extractUid(request)
        val path = request.url.encodedPath
        val method = request.method
        val bodyStr = readBody(request)

        if (path.endsWith("/functions/v1/email-otp")) {
            val reqJson = JSONObject(bodyStr)
            val action = reqJson.optString("action")
            val email = reqJson.optString("email").trim().lowercase()
            when (action) {
                "send_register_otp" -> {
                    val code = String.format("%06d", ((100_000 + (seq * 7919)) % 900_000))
                    otpRecords["$email:register"] = OtpRecord(
                        code = code,
                        fullName = reqJson.optString("full_name", "Utilizador Socializa"),
                        expiresAtMs = System.currentTimeMillis() + 300_000L
                    )
                    return jsonResponse(request, 200, JSONObject().put("ok", true).put("expires_in_seconds", 300).toString())
                }
                "verify_register_otp" -> {
                    val rec = otpRecords["$email:register"]
                        ?: return jsonResponse(request, 400, JSONObject().put("ok", false).put("error", "Código não encontrado.").toString())
                    if (System.currentTimeMillis() > rec.expiresAtMs) {
                        return jsonResponse(request, 400, JSONObject().put("ok", false).put("error", "Código expirado (5 min).").toString())
                    }
                    if (rec.code != reqJson.optString("otp")) {
                        return jsonResponse(request, 400, JSONObject().put("ok", false).put("error", "Código incorreto.").toString())
                    }
                    val uid = nextId("emailuid")
                    emailPasswords[email] = reqJson.optString("password")
                    emailUids[email] = uid
                    registerRawUser(uid, email.substringBefore("@"), reqJson.optString("full_name", rec.fullName))
                    otpRecords.remove("$email:register")
                    return jsonResponse(request, 200, JSONObject().put("ok", true).put("uid", uid).toString())
                }
                "send_reset_otp" -> {
                    val code = String.format("%06d", ((200_000 + (seq * 3571)) % 900_000))
                    otpRecords["$email:reset"] = OtpRecord(
                        code = code,
                        fullName = "",
                        expiresAtMs = System.currentTimeMillis() + 300_000L
                    )
                    return jsonResponse(request, 200, JSONObject().put("ok", true).put("expires_in_seconds", 300).toString())
                }
                "verify_reset_otp" -> {
                    val rec = otpRecords["$email:reset"]
                        ?: return jsonResponse(request, 400, JSONObject().put("ok", false).put("error", "Código não encontrado.").toString())
                    if (System.currentTimeMillis() > rec.expiresAtMs || rec.code != reqJson.optString("otp")) {
                        return jsonResponse(request, 400, JSONObject().put("ok", false).put("error", "Código inválido ou expirado.").toString())
                    }
                    emailPasswords[email] = reqJson.optString("new_password")
                    otpRecords.remove("$email:reset")
                    return jsonResponse(request, 200, JSONObject().put("ok", true).put("message", "Senha atualizada com sucesso!").toString())
                }
            }
        }

        if (path.endsWith("/functions/v1/firebase-session")) {
            val headerFbToken = request.header("X-Firebase-Token").orEmpty()
            val bodyFbToken = runCatching { JSONObject(bodyStr).optString("firebase_token") }.getOrDefault("")
            if (headerFbToken.isBlank() && bodyFbToken.isBlank() && rawToken.isBlank()) {
                return jsonResponse(request, 401, """{"error":"Missing Firebase token"}""")
            }
            return if (exchangeEnabled && exchangeTargetUid.isNotBlank()) {
                jsonResponse(
                    request,
                    200,
                    JSONObject()
                        .put("access_token", "exchanged-$exchangeTargetUid")
                        .put("expires_in", 3300)
                        .put("token_type", "bearer")
                        .put("user_id", exchangeTargetUid)
                        .toString()
                )
            } else {
                jsonResponse(request, 404, """{"error":"Function not deployed"}""")
            }
        }

        if (rawToken in rejectedTokens) {
            return jsonResponse(request, 401, """{"message":"Invalid JWT or missing role claim"}""")
        }

        return when {
            path.endsWith("/rpc/ensure_firebase_profile") -> {
                val reqJson = JSONObject(bodyStr)
                val fullName = reqJson.optString("p_full_name", "Utilizador")
                val existing = profiles[uid]
                val profile = existing ?: JSONObject().apply {
                    put("id", uid)
                    put("username", fullName.lowercase().replace(" ", "_") + "_" + uid.takeLast(4))
                    put("full_name", fullName)
                    put("bio", "")
                    put("country", reqJson.optString("p_country", "Angola"))
                    put("province", reqJson.optString("p_province", "Luanda"))
                    put("is_private", false)
                    put("is_verified", false)
                    put("role", "user")
                    put("is_suspended", false)
                    put("followers_count", 0)
                    put("following_count", 0)
                    put("posts_count", 0)
                    put("is_online", true)
                    profiles[uid] = this
                }
                jsonResponse(request, 200, profile.toString())
            }

            path.endsWith("/rest/v1/profiles") && method == "GET" -> {
                val idParam = request.url.queryParameter("id")?.removePrefix("eq.")
                val orParam = request.url.queryParameter("or")
                val list = profiles.values.filter { p ->
                    val pid = p.getString("id")
                    val blocked = (uid to pid) in blocks || (pid to uid) in blocks
                    if (blocked) return@filter false
                    val matchId = idParam == null || pid == idParam
                    val matchOr = if (orParam != null && orParam.contains("*")) {
                        val term = orParam.substringAfter("*").substringBefore("*")
                        p.getString("full_name").contains(term, ignoreCase = true) ||
                            p.getString("username").contains(term, ignoreCase = true)
                    } else true
                    matchId && matchOr
                }
                jsonResponse(request, 200, JSONArray(list).toString())
            }

            path.endsWith("/rpc/claim_daily_verified_badge") -> {
                val reqJson = JSONObject(bodyStr)
                val adsWatched = reqJson.optInt("p_ads_watched", 0)
                if (adsWatched < 5) {
                    return jsonResponse(request, 400, """{"message":"Assiste a 5 anúncios"}""")
                }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val untilIso = sdf.format(java.util.Date(System.currentTimeMillis() + 86_400_000L))
                val p = profiles[uid] ?: return jsonResponse(request, 404, "{}")
                p.put("verified_until", untilIso)
                jsonResponse(request, 200, p.toString())
            }

            path.endsWith("/rest/v1/profiles") && method == "PATCH" -> {
                val updates = JSONObject(bodyStr)
                val p = profiles[uid] ?: return jsonResponse(request, 404, "{}")
                updates.keys().forEach { k -> p.put(k, updates.get(k)) }
                jsonResponse(request, 204, "")
            }

            path.startsWith("/storage/v1/object/media/") -> {
                jsonResponse(request, 200, JSONObject().put("Key", path.removePrefix("/storage/v1/object/")).toString())
            }

            path.endsWith("/rest/v1/posts") && method == "POST" -> {
                val obj = JSONObject(bodyStr)
                val id = nextId("post")
                val row = JSONObject()
                    .put("id", id)
                    .put("author_id", uid)
                    .put("post_type", obj.optString("post_type", "text"))
                    .put("caption", obj.optString("caption", ""))
                    .put("hashtags", obj.optJSONArray("hashtags") ?: JSONArray())
                    .put("category", obj.opt("category") ?: JSONObject.NULL)
                    .put("location_label", obj.opt("location_label") ?: JSONObject.NULL)
                    .put("visibility", obj.optString("visibility", "public"))
                    .put("likes_count", 0)
                    .put("comments_count", 0)
                    .put("shares_count", 0)
                    .put("views_count", 0)
                    .put("created_at", "2026-11-22T12:10:00Z")
                posts.add(0, row)
                profiles[uid]?.let { it.put("posts_count", it.optInt("posts_count") + 1) }
                jsonResponse(request, 201, JSONArray().put(row).toString())
            }

            path.endsWith("/rest/v1/posts") && method == "GET" -> {
                feedGetCount.incrementAndGet()
                val limit = request.url.queryParameter("limit")?.toIntOrNull() ?: 15
                val offset = request.url.queryParameter("offset")?.toIntOrNull() ?: 0
                val visible = posts.filter { p ->
                    val authorId = p.getString("author_id")
                    if ((uid to authorId) in blocks || (authorId to uid) in blocks) return@filter false
                    when (p.optString("visibility", "public")) {
                        "public" -> true
                        "followers" -> authorId == uid || (uid to authorId) in follows
                        "private" -> authorId == uid
                        else -> true
                    }
                }
                val slice = visible.drop(offset).take(limit).map { p ->
                    val enriched = JSONObject(p.toString())
                    val authorId = p.getString("author_id")
                    enriched.put("author", profiles[authorId] ?: JSONObject().put("id", authorId))
                    val postMedia = media.filter { it.optString("post_id") == p.getString("id") }
                    enriched.put("media", JSONArray(postMedia))
                    enriched
                }
                jsonResponse(request, 200, JSONArray(slice).toString())
            }

            path.endsWith("/rest/v1/posts") && method == "PATCH" -> {
                val id = request.url.queryParameter("id")?.removePrefix("eq.")
                val post = posts.firstOrNull { it.getString("id") == id && it.getString("author_id") == uid }
                    ?: return jsonResponse(request, 403, "{}")
                val updates = JSONObject(bodyStr)
                updates.keys().forEach { k -> post.put(k, updates.get(k)) }
                jsonResponse(request, 204, "")
            }

            path.endsWith("/rest/v1/posts") && method == "DELETE" -> {
                val id = request.url.queryParameter("id")?.removePrefix("eq.")
                val removed = posts.removeAll { it.getString("id") == id && it.getString("author_id") == uid }
                jsonResponse(request, if (removed) 204 else 403, "")
            }

            path.endsWith("/rest/v1/media") && method == "POST" -> {
                media.add(JSONObject(bodyStr))
                jsonResponse(request, 201, "{}")
            }

            path.endsWith("/rest/v1/videos") && method == "POST" -> {
                val obj = JSONObject(bodyStr)
                val id = nextId("vid")
                val row = JSONObject()
                    .put("id", id)
                    .put("author_id", uid)
                    .put("playback_url", obj.optString("playback_url", ""))
                    .put("thumbnail_url", obj.opt("thumbnail_url") ?: JSONObject.NULL)
                    .put("caption", obj.optString("caption", ""))
                    .put("hashtags", obj.optJSONArray("hashtags") ?: JSONArray())
                    .put("music_title", obj.optString("music_title", "Som original"))
                    .put("music_url", obj.opt("music_url") ?: JSONObject.NULL)
                    .put("effects_tags", JSONArray())
                    .put("likes_count", 0)
                    .put("comments_count", 0)
                    .put("shares_count", 0)
                    .put("views_count", 0)
                    .put("created_at", "2026-11-22T12:12:00Z")
                videos.add(0, row)
                jsonResponse(request, 201, JSONArray().put(row).toString())
            }

            path.endsWith("/rest/v1/videos") && method == "GET" -> {
                val list = videos.map { v ->
                    JSONObject(v.toString()).put("author", profiles[v.getString("author_id")])
                }
                jsonResponse(request, 200, JSONArray(list).toString())
            }

            path.endsWith("/rest/v1/likes") && method == "GET" -> {
                val arr = JSONArray(likes.filter { it.first == uid }.map {
                    JSONObject().put("user_id", it.first).put("post_id", it.second)
                })
                jsonResponse(request, 200, arr.toString())
            }

            path.endsWith("/rest/v1/likes") && method == "POST" -> {
                val obj = JSONObject(bodyStr)
                val postId = obj.getString("post_id")
                if (!likes.add(uid to postId)) {
                    return jsonResponse(request, 409, "{\"code\":\"23505\"}")
                }
                val post = posts.firstOrNull { it.getString("id") == postId }
                if (post != null) {
                    post.put("likes_count", post.optInt("likes_count") + 1)
                    val authorId = post.getString("author_id")
                    if (authorId != uid) {
                        addNotification(authorId, uid, "like", "curtiu a tua publicação", post.optString("caption"), postId)
                    }
                }
                jsonResponse(request, 201, "{}")
            }

            path.endsWith("/rest/v1/likes") && method == "DELETE" -> {
                val postId = request.url.queryParameter("post_id")?.removePrefix("eq.") ?: ""
                if (likes.remove(uid to postId)) {
                    posts.firstOrNull { it.getString("id") == postId }?.let {
                        it.put("likes_count", (it.optInt("likes_count") - 1).coerceAtLeast(0))
                    }
                }
                jsonResponse(request, 204, "")
            }

            path.endsWith("/rest/v1/post_views") && method == "POST" -> {
                val postId = JSONObject(bodyStr).getString("post_id")
                if (postViews.add(postId to uid)) {
                    posts.firstOrNull { it.getString("id") == postId }?.let {
                        it.put("views_count", it.optInt("views_count") + 1)
                    }
                }
                jsonResponse(request, 201, "{}")
            }

            path.endsWith("/rest/v1/comments") && method == "GET" -> {
                val postId = request.url.queryParameter("post_id")?.removePrefix("eq.")
                val list = comments.filter { c -> postId == null || c.optString("post_id") == postId }.map { c ->
                    JSONObject(c.toString()).put("author", profiles[c.getString("author_id")])
                }
                jsonResponse(request, 200, JSONArray(list).toString())
            }

            path.endsWith("/rest/v1/comments") && method == "POST" -> {
                val obj = JSONObject(bodyStr)
                val id = nextId("comment")
                val postId = obj.optString("post_id").takeIf { it.isNotBlank() }
                val parentId = obj.optString("parent_id").takeIf { it.isNotBlank() && it != "null" }
                val row = JSONObject()
                    .put("id", id)
                    .put("post_id", postId ?: JSONObject.NULL)
                    .put("video_id", obj.opt("video_id") ?: JSONObject.NULL)
                    .put("author_id", uid)
                    .put("parent_id", parentId ?: JSONObject.NULL)
                    .put("body", obj.getString("body"))
                    .put("likes_count", 0)
                    .put("created_at", "2026-11-22T12:15:00Z")
                comments.add(row)
                if (postId != null) {
                    posts.firstOrNull { it.getString("id") == postId }?.let { p ->
                        p.put("comments_count", p.optInt("comments_count") + 1)
                        val owner = p.getString("author_id")
                        if (owner != uid) {
                            addNotification(owner, uid, "comment", "comentou a tua publicação", obj.getString("body"), postId)
                        }
                    }
                }
                if (parentId != null) {
                    comments.firstOrNull { it.getString("id") == parentId }?.let { parent ->
                        val parentAuthor = parent.getString("author_id")
                        if (parentAuthor != uid) {
                            addNotification(parentAuthor, uid, "comment_reply", "respondeu ao teu comentário", obj.getString("body"), postId ?: parentId)
                        }
                    }
                }
                jsonResponse(request, 201, JSONArray().put(row).toString())
            }

            path.endsWith("/rest/v1/comment_likes") && method == "GET" -> {
                val arr = JSONArray(commentLikes.filter { it.first == uid }.map {
                    JSONObject().put("user_id", it.first).put("comment_id", it.second)
                })
                jsonResponse(request, 200, arr.toString())
            }

            path.endsWith("/rest/v1/comment_likes") && method == "POST" -> {
                val cid = JSONObject(bodyStr).getString("comment_id")
                if (commentLikes.add(uid to cid)) {
                    comments.firstOrNull { it.getString("id") == cid }?.let {
                        it.put("likes_count", it.optInt("likes_count") + 1)
                    }
                }
                jsonResponse(request, 201, "{}")
            }

            path.endsWith("/rest/v1/follows") && method == "GET" -> {
                val arr = JSONArray(follows.filter { it.first == uid }.map {
                    JSONObject().put("follower_id", it.first).put("following_id", it.second).put("status", "accepted")
                })
                jsonResponse(request, 200, arr.toString())
            }

            path.endsWith("/rest/v1/follows") && method == "POST" -> {
                val target = JSONObject(bodyStr).getString("following_id")
                if ((uid to target) in blocks || (target to uid) in blocks) {
                    return jsonResponse(request, 403, "{}")
                }
                if (follows.add(uid to target)) {
                    profiles[target]?.let { it.put("followers_count", it.optInt("followers_count") + 1) }
                    profiles[uid]?.let { it.put("following_count", it.optInt("following_count") + 1) }
                    addNotification(target, uid, "follow", "Novo seguidor", "Começou a seguir-te.", uid)
                }
                jsonResponse(request, 201, "{}")
            }

            path.endsWith("/rest/v1/blocks") && method == "GET" -> {
                val arr = JSONArray(blocks.filter { it.first == uid }.map {
                    JSONObject().put("blocker_id", it.first).put("blocked_id", it.second)
                })
                jsonResponse(request, 200, arr.toString())
            }

            path.endsWith("/rest/v1/blocks") && method == "POST" -> {
                val blocked = JSONObject(bodyStr).getString("blocked_id")
                blocks.add(uid to blocked)
                follows.remove(uid to blocked)
                follows.remove(blocked to uid)
                jsonResponse(request, 201, "{}")
            }

            path.endsWith("/rpc/get_or_create_direct_conversation") -> {
                val target = JSONObject(bodyStr).getString("p_target_user_id")
                if ((uid to target) in blocks || (target to uid) in blocks) {
                    return jsonResponse(request, 403, "{\"message\":\"Blocked\"}")
                }
                val existingId = conversations.firstOrNull { c ->
                    !c.getBoolean("is_group") &&
                        conversationMembers.any { it.first == c.getString("id") && it.second == uid } &&
                        conversationMembers.any { it.first == c.getString("id") && it.second == target }
                }?.getString("id")
                val convId = existingId ?: nextId("conv").also { id ->
                    conversations.add(
                        JSONObject()
                            .put("id", id)
                            .put("is_group", false)
                            .put("last_message_preview", "")
                            .put("last_message_at", "2026-11-22T12:20:00Z")
                    )
                    conversationMembers.add(id to uid)
                    conversationMembers.add(id to target)
                }
                jsonResponse(request, 200, "\"$convId\"")
            }

            path.endsWith("/rest/v1/conversations") && method == "GET" -> {
                val myConvs = conversations.filter { c ->
                    conversationMembers.any { it.first == c.getString("id") && it.second == uid }
                }.map { c ->
                    val membersArr = JSONArray()
                    conversationMembers.filter { it.first == c.getString("id") }.forEach { (_, mUid) ->
                        membersArr.put(
                            JSONObject()
                                .put("user_id", mUid)
                                .put("last_read_at", "2026-11-22T12:20:00Z")
                                .put("profile", profiles[mUid])
                        )
                    }
                    JSONObject(c.toString()).put("members", membersArr)
                }
                jsonResponse(request, 200, JSONArray(myConvs).toString())
            }

            path.endsWith("/rest/v1/messages") && method == "GET" -> {
                val convId = request.url.queryParameter("conversation_id")?.removePrefix("eq.")
                val list = messages.filter { it.getString("conversation_id") == convId }.map { m ->
                    JSONObject(m.toString()).put("sender", profiles[m.getString("sender_id")])
                }
                jsonResponse(request, 200, JSONArray(list).toString())
            }

            path.endsWith("/rest/v1/messages") && method == "POST" -> {
                val obj = JSONObject(bodyStr)
                val convId = obj.getString("conversation_id")
                val id = nextId("msg")
                val row = JSONObject()
                    .put("id", id)
                    .put("conversation_id", convId)
                    .put("sender_id", uid)
                    .put("message_type", obj.optString("message_type", "text"))
                    .put("body", obj.optString("body", ""))
                    .put("delivery_status", "sent")
                    .put("created_at", "2026-11-22T12:25:00Z")
                messages.add(row)
                conversations.firstOrNull { it.getString("id") == convId }?.put("last_message_preview", obj.optString("body"))
                conversationMembers.filter { it.first == convId && it.second != uid }.forEach { (_, rec) ->
                    addNotification(rec, uid, "message", "Nova mensagem", obj.optString("body"), convId)
                }
                jsonResponse(request, 201, JSONArray().put(row).toString())
            }

            path.endsWith("/rpc/mark_conversation_read") -> {
                val convId = JSONObject(bodyStr).getString("p_conversation_id")
                messages.filter { it.getString("conversation_id") == convId && it.getString("sender_id") != uid }
                    .forEach { it.put("delivery_status", "read") }
                jsonResponse(request, 200, "")
            }

            path.endsWith("/rest/v1/notifications") && method == "GET" -> {
                val list = notifications.filter { it.getString("recipient_id") == uid }
                jsonResponse(request, 200, JSONArray(list).toString())
            }

            path.endsWith("/rest/v1/products") && method == "GET" -> {
                val list = products.map { p ->
                    JSONObject(p.toString()).put("seller", profiles[p.getString("seller_id")])
                }
                jsonResponse(request, 200, JSONArray(list).toString())
            }

            path.endsWith("/rest/v1/products") && method == "POST" -> {
                val obj = JSONObject(bodyStr)
                val id = nextId("prod")
                val row = JSONObject()
                    .put("id", id)
                    .put("seller_id", uid)
                    .put("title", obj.optString("title", ""))
                    .put("description", obj.optString("description", ""))
                    .put("category", obj.optString("category", "Eletrónicos"))
                    .put("price_kz", obj.optLong("price_kz", 0L))
                    .put("province", obj.optString("province", "Luanda"))
                    .put("image_url", obj.opt("image_url") ?: JSONObject.NULL)
                    .put("status", "active")
                products.add(0, row)
                jsonResponse(request, 201, JSONArray().put(row).toString())
            }

            else -> {
                val defaultBody = if (method == "GET") "[]" else "{}"
                jsonResponse(request, 200, defaultBody)
            }
        }
    }

    private fun addNotification(recipientId: String, actorId: String, type: String, title: String, body: String, refId: String) {
        notifications.add(
            0,
            JSONObject()
                .put("id", nextId("notif"))
                .put("recipient_id", recipientId)
                .put("actor_id", actorId)
                .put("event_type", type)
                .put("title", title)
                .put("body", body)
                .put("reference_id", refId)
                .put("is_read", false)
                .put("created_at", "2026-11-22T12:30:00Z")
        )
    }
}
