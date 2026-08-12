package one.astroport.atom4love.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import one.astroport.atom4love.BuildConfig
import one.astroport.atom4love.R
import one.astroport.atom4love.data.MultipassAccount
import one.astroport.atom4love.multipass.EnrollError
import one.astroport.atom4love.multipass.Enrollment
import one.astroport.atom4love.ui.components.SectionLabel
import one.astroport.atom4love.ui.components.glass
import one.astroport.atom4love.ui.components.screenBackground
import one.astroport.atom4love.ui.theme.A4L
import one.astroport.atom4love.ui.theme.A4LText
import one.astroport.atom4love.ui.theme.tint

/**
 * 06 · MULTIPASS — l'entrée sur Astroport.ONE.
 *
 * Cet écran ne fabrique rien : il explique, demande une adresse, et transmet.
 * Tout le reste — clés, portefeuilles, uDRIVE, clé LOVE — est l'ouvrage de la
 * station. Ce qu'il doit à l'utilisateur, en revanche, c'est la vérité entière
 * avant qu'il ne s'engage : que l'app marche très bien sans compte, que
 * l'endroit où il va est un bac à sable, et que son noyau actuel va céder la
 * place.
 *
 * Il s'ouvre juste après la forge, et se retrouve à tout moment par le bas de
 * l'onglet Noyau : refuser aujourd'hui n'est pas refuser pour toujours.
 */
@Composable
fun MultipassScreen(
    step: Enrollment.Step,
    account: MultipassAccount?,
    onSubmit: (email: String, passCode: String?) -> Unit,
    onRetryActivation: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf(account?.email ?: "") }
    var passCode by rememberSaveable { mutableStateOf("") }
    var revealNsec by remember { mutableStateOf(false) }
    val busy = step is Enrollment.Step.Creating || step is Enrollment.Step.Activating
    val needPass = step is Enrollment.Step.NeedPass

    // Un compte est déjà là, et rien n'est en cours : cet écran n'a plus à
    // demander une adresse, mais à offrir la seule manœuvre qui reste utile —
    // redemander la clé LOVE. C'est la sortie du noyau reforgé avec d'autres
    // données : la clé rangée ici ne correspond alors plus à la naissance
    // saisie, et seule la station peut en dériver la bonne.
    val existing = account?.takeIf { step is Enrollment.Step.Idle }

    Column(
        modifier
            .fillMaxSize()
            .screenBackground(A4L.GlowBond, A4L.DeepAlt, radiusFactor = 1.4f)
            .statusBarsPadding(),
    ) {

        // ── Barre d'état applicative ──────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚛", color = A4L.Cyan, fontSize = 13.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "ATOM4LOVE",
                    style = A4LText.Data.copy(letterSpacing = 1.7.sp),
                    color = A4L.TextMuted,
                )
            }
            // Pendant que la station travaille, la sortie disparaît : fermer
            // l'écran ne l'arrêterait pas, et laisserait un compte à moitié né.
            if (!busy) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(A4L.Glass, CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 13.sp, color = A4L.TextStrong) }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(top = 18.dp)) {
                Text(
                    stringResource(
                        when {
                            step is Enrollment.Step.Done -> R.string.mp_title_done
                            existing != null -> R.string.mp_title_existing
                            else -> R.string.mp_title_new
                        },
                    ),
                    style = A4LText.H1,
                    color = A4L.TextHigh,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when {
                            step is Enrollment.Step.Done -> R.string.mp_lead_done
                            existing != null -> R.string.mp_lead_existing
                            else -> R.string.mp_lead_new
                        },
                    ),
                    style = A4LText.Body,
                    color = A4L.TextMuted,
                )
            }

            Spacer(Modifier.height(20.dp))

            when {
                step is Enrollment.Step.Done -> Success(
                    account = step.account,
                    revealNsec = revealNsec,
                    onToggleNsec = { revealNsec = !revealNsec },
                )

                existing != null -> Existing(
                    account = existing,
                    revealNsec = revealNsec,
                    onToggleNsec = { revealNsec = !revealNsec },
                )

                else -> {
                    Explanation()
                    Spacer(Modifier.height(16.dp))

                    // ── L'adresse ─────────────────────────────────────────
                    SectionLabel(stringResource(R.string.mp_email_label))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .glass(11.dp, A4L.Glass, A4L.Stroke)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = email,
                            onValueChange = { email = it.trim() },
                            enabled = !busy,
                            singleLine = true,
                            textStyle = A4LText.Body.copy(fontSize = 14.sp, color = A4L.TextHigh),
                            cursorBrush = SolidColor(A4L.Cyan),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() },
                            ),
                            decorationBox = { inner ->
                                if (email.isEmpty()) {
                                    Text(
                                        stringResource(R.string.mp_email_placeholder),
                                        style = A4LText.Body.copy(fontSize = 14.sp),
                                        color = A4L.TextGhost,
                                    )
                                }
                                inner()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.mp_email_hint),
                        style = A4LText.Caption,
                        color = A4L.TextMuted,
                    )

                    // ── Le code PASS, quand la station le réclame ─────────
                    if (needPass) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .glass(12.dp, A4L.Indigo.tint(0.06f), A4L.Indigo.tint(0.26f))
                                .padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text(
                                stringResource(R.string.mp_pass_needed_title),
                                style = A4LText.Body.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = A4L.Indigo,
                            )
                            Text(
                                stringResource(R.string.mp_pass_needed_body),
                                style = A4LText.Caption,
                                color = A4L.TextMuted,
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .glass(10.dp, A4L.Glass, A4L.Stroke)
                                    .padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicTextField(
                                    value = passCode,
                                    onValueChange = { passCode = it.trim() },
                                    enabled = !busy,
                                    singleLine = true,
                                    textStyle = A4LText.Data.copy(
                                        fontSize = 15.sp,
                                        color = A4L.TextHigh,
                                    ),
                                    cursorBrush = SolidColor(A4L.Cyan),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { focusManager.clearFocus() },
                                    ),
                                    decorationBox = { inner ->
                                        if (passCode.isEmpty()) {
                                            Text(
                                                stringResource(R.string.mp_pass_placeholder),
                                                style = A4LText.Data.copy(fontSize = 15.sp),
                                                color = A4L.TextGhost,
                                            )
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    // ── Ce que la station est en train de faire ───────────
                    if (busy) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .glass(12.dp, A4L.Glass, A4L.Stroke)
                                .padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚛", fontSize = 13.sp, color = A4L.Indigo)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(
                                    if (step is Enrollment.Step.Creating) {
                                        R.string.mp_busy_creating
                                    } else {
                                        R.string.mp_busy_activating
                                    },
                                ),
                                style = A4LText.Caption,
                                color = A4L.TextMuted,
                            )
                        }
                    }

                    // ── L'échec, et ce qu'il reste à faire ────────────────
                    if (step is Enrollment.Step.Failed) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .glass(12.dp, A4L.Red.tint(0.06f), A4L.Red.tint(0.28f))
                                .padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.mp_failed, reasonText(step.reason)),
                                style = A4LText.Caption,
                                color = A4L.Red.copy(alpha = 0.9f),
                            )
                            if (step.recoverable) {
                                Text(
                                    stringResource(R.string.mp_failed_recoverable),
                                    style = A4LText.Caption,
                                    color = A4L.TextMuted,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Actions ───────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                step is Enrollment.Step.Done -> ActionButton(
                    label = stringResource(R.string.mp_action_back),
                    accent = A4L.Mint,
                    enabled = true,
                    onClick = onClose,
                )

                existing != null -> {
                    ActionButton(
                        label = stringResource(
                            if (existing.loveActivated) {
                                R.string.mp_action_rederive
                            } else {
                                R.string.mp_action_obtain
                            },
                        ),
                        accent = A4L.Indigo,
                        enabled = true,
                        onClick = onRetryActivation,
                    )
                    LaterButton(onClose)
                }

                step is Enrollment.Step.Failed -> {
                    ActionButton(
                        label = stringResource(
                            if (step.recoverable) {
                                R.string.mp_action_ask_again
                            } else {
                                R.string.mp_action_retry
                            },
                        ),
                        accent = A4L.Indigo,
                        enabled = true,
                        onClick = {
                            if (step.recoverable) onRetryActivation() else onReset()
                        },
                    )
                    LaterButton(onClose)
                }

                else -> {
                    ActionButton(
                        label = stringResource(
                            when {
                                busy -> R.string.mp_action_busy
                                needPass -> R.string.mp_action_recover
                                else -> R.string.mp_action_create
                            },
                        ),
                        accent = A4L.Indigo,
                        enabled = !busy && email.contains("@") && email.contains(".") &&
                            (!needPass || passCode.isNotEmpty()),
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit(email, passCode.ifBlank { null })
                        },
                    )
                    if (!busy) LaterButton(onClose)
                }
            }
        }
    }
}

/**
 * Le compte tel qu'il est aujourd'hui, et la seule manœuvre qui reste : faire
 * redériver la clé LOVE.
 *
 * Elle sert surtout après une reforge — noyau dissous, cinq données ressaisies
 * autrement. La clé rangée ici vient de l'ancienne naissance ; personne d'autre
 * que la station ne peut en produire la nouvelle, et elle le fait sans toucher
 * au compte ni aux portefeuilles.
 */
@Composable
private fun Existing(
    account: MultipassAccount,
    revealNsec: Boolean,
    onToggleNsec: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Mint.tint(0.05f), A4L.Mint.tint(0.22f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Field(stringResource(R.string.mp_field_account), account.email)
            Field(
                stringResource(R.string.mp_field_station),
                account.station.removePrefix("https://"),
            )
            Field(
                stringResource(R.string.mp_field_love_key),
                account.loveNpub.ifEmpty { stringResource(R.string.mp_love_not_derived) },
            )
            Field(stringResource(R.string.mp_field_account_npub), account.npub)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Indigo.tint(0.05f), A4L.Indigo.tint(0.22f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(
                    if (account.loveActivated) {
                        R.string.mp_rederive_title
                    } else {
                        R.string.mp_obtain_title
                    },
                ),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Indigo,
            )
            Text(
                stringResource(R.string.mp_rederive_body),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }

        // Le PASS reste consultable : c'est lui qui rouvre le compte ailleurs.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Glass, A4L.Stroke)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Field(stringResource(R.string.mp_field_pass), account.pass.ifEmpty { "—" })
            Text(
                if (revealNsec) account.nsec else stringResource(R.string.mp_reveal_key),
                style = if (revealNsec) {
                    A4LText.Data.copy(fontSize = 10.sp)
                } else {
                    A4LText.Caption.copy(fontWeight = FontWeight.SemiBold)
                },
                color = if (revealNsec) A4L.TextStrong else A4L.Indigo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleNsec)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

/** Le discours : ce qu'on gagne, où l'on met les pieds, ce qu'on perd. */
@Composable
private fun Explanation() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Ce que l'app fait déjà toute seule — dit en premier, pour que
        // l'inscription reste un choix et non un péage.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Glass, A4L.Stroke)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.mp_no_account_title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.TextHigh,
            )
            Text(
                stringResource(R.string.mp_no_account_body),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }

        // Ce que la station ajoute.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Indigo.tint(0.05f), A4L.Indigo.tint(0.22f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.mp_adds_title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Indigo,
            )
            Bullet(stringResource(R.string.mp_adds_identity))
            Bullet(stringResource(R.string.mp_adds_love))
            Bullet(stringResource(R.string.mp_adds_wallet))
            Bullet(stringResource(R.string.mp_adds_web))
        }

        // Où l'on met les pieds. Le mot « bac à sable » est celui d'Astroport.ONE.
        if (BuildConfig.ASTROPORT_ORIGIN) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(12.dp, A4L.Amber.tint(0.06f), A4L.Amber.tint(0.26f))
                    .padding(13.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row {
                    Text("⚠", fontSize = 12.sp, color = A4L.Amber)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        stringResource(R.string.mp_sandbox_title),
                        style = A4LText.Body.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = A4L.Amber,
                    )
                }
                Text(
                    stringResource(R.string.mp_sandbox_body),
                    style = A4LText.Caption,
                    color = A4L.Amber.copy(alpha = 0.85f),
                )
                Text(
                    stringResource(R.string.mp_sandbox_keys),
                    style = A4LText.Caption,
                    color = A4L.TextMuted,
                )
            }
        }

        // Ce que l'inscription change ici, sur l'appareil.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Amber.tint(0.05f), A4L.Amber.tint(0.20f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.mp_provisional_title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Amber,
            )
            Text(
                stringResource(R.string.mp_provisional_body),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }
    }
}

/** Le compte ouvert : ce qu'il faut noter, et ce que l'appareil garde. */
@Composable
private fun Success(
    account: MultipassAccount,
    revealNsec: Boolean,
    onToggleNsec: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Mint.tint(0.06f), A4L.Mint.tint(0.26f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Field(stringResource(R.string.mp_field_account), account.email)
            Field(
                stringResource(R.string.mp_field_station),
                account.station.removePrefix("https://"),
            )
            if (account.loveNpub.isNotEmpty()) {
                Field(stringResource(R.string.mp_field_love_key), account.loveNpub)
            }
            Field(stringResource(R.string.mp_field_account_npub), account.npub)
        }

        // Le PASS : la seule chose que l'utilisateur doive vraiment recopier.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Amber.tint(0.06f), A4L.Amber.tint(0.26f))
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.mp_note_pass_title),
                style = A4LText.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                color = A4L.Amber,
            )
            Text(
                account.pass,
                style = A4LText.Metric.copy(letterSpacing = 6.sp),
                color = A4L.TextHigh,
            )
            Text(
                stringResource(R.string.mp_note_pass_body),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
        }

        // La clé du compte reste au coffre ; on ne la montre qu'à la demande.
        Column(
            Modifier
                .fillMaxWidth()
                .glass(12.dp, A4L.Glass, A4L.Stroke)
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                stringResource(R.string.mp_key_vaulted),
                style = A4LText.Caption,
                color = A4L.TextMuted,
            )
            Text(
                if (revealNsec) account.nsec else stringResource(R.string.mp_reveal_key),
                style = if (revealNsec) {
                    A4LText.Data.copy(fontSize = 10.sp)
                } else {
                    A4LText.Caption.copy(fontWeight = FontWeight.SemiBold)
                },
                color = if (revealNsec) A4L.TextStrong else A4L.Indigo,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleNsec)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = A4LText.Caption, color = A4L.TextFaint)
        Text(
            value,
            style = A4LText.Data.copy(fontSize = 11.sp),
            color = A4L.TextStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("·", style = A4LText.Caption, color = A4L.TextFaint)
        Spacer(Modifier.width(8.dp))
        Text(text, style = A4LText.Caption, color = A4L.TextMuted)
    }
}

@Composable
private fun ActionButton(
    label: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glass(
                radius = 13.dp,
                background = accent.tint(if (enabled) 0.12f else 0.04f),
                border = accent.tint(if (enabled) 0.40f else 0.14f),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = A4LText.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = if (enabled) accent else A4L.TextGhost,
        )
    }
}

/** Refuser aujourd'hui n'est pas refuser pour toujours — et on le dit. */
@Composable
private fun LaterButton(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.mp_later),
                style = A4LText.Body.copy(fontSize = 13.sp),
                color = A4L.TextMuted,
            )
        }
        Text(
            stringResource(R.string.mp_later_hint),
            style = A4LText.Caption,
            color = A4L.TextGhost,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * La raison d'un échec, mise en mots. Sauf pour [EnrollError.FromStation] :
 * ce message-là vient d'Astroport.ONE, dans la langue qu'elle a employée, et
 * on le rend tel quel plutôt que de le taire.
 */
@Composable
private fun reasonText(error: EnrollError): String =
    EnrollError.messageRes(error)?.let { stringResource(it) }
        ?: (error as EnrollError.FromStation).message
