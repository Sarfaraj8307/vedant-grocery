package com.vedant.admin.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vedant.admin.core.error.ErrorCopy
import com.vedant.admin.ui.components.PrimaryButton
import com.vedant.admin.ui.theme.DURATION_SLOW
import com.vedant.admin.ui.theme.EasingDecelerate
import com.vedant.admin.ui.theme.Leaf100
import com.vedant.admin.ui.theme.Leaf600
import com.vedant.admin.ui.theme.Leaf700
import com.vedant.admin.ui.theme.Leaf800
import com.vedant.admin.ui.theme.LocalReduceMotion
import com.vedant.admin.ui.theme.SurfaceWhite
import com.vedant.admin.ui.theme.Danger
import com.vedant.admin.ui.theme.DangerBg
import com.vedant.admin.ui.theme.VedantTheme

/**
 * S2 — Sign in.
 *
 * Email + password. The submit button shows an in-flight spinner and is disabled
 * while a request is running, so a double tap cannot fire two sign-ins.
 *
 * The screen opens with a gradient hero panel containing the brand icon and
 * title, followed by the form fields on the paper background. The hero
 * elements scale/fade in on mount (respected by `LocalReduceMotion`).
 */
@Composable
fun SignInScreen(
    factory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    val viewModel: SignInViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SignInContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePasswordVisibility = viewModel::onTogglePasswordVisibility,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
private fun SignInContent(
    state: SignInUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VedantTheme.spacing
    val sizing = VedantTheme.sizing
    val reduceMotion = LocalReduceMotion.current

    // Capture the error fields as stable locals: smart-cast inside the
    // `supportingText` composable lambdas is otherwise unreliable.
    val emailError = state.emailError
    val passwordError = state.passwordError
    val submitError = state.error

    // ── Hero entrance animation ───────────────────────────────────────
    val heroProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            heroProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = DURATION_SLOW,
                    easing = EasingDecelerate,
                ),
            )
        } else {
            heroProgress.snapTo(1f)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            // ── Gradient hero header ────────────────────────────────────
            // A compact gradient panel carries the brand identity and
            // gives the login a premium, non-generic feel. The icon sits
            // in a tinted circle so it reads as a logo, not a stray glyph.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Leaf700, Leaf800)))
                    .padding(
                        start = spacing.xl,
                        end = spacing.xl,
                        top = spacing.xxl,
                        bottom = spacing.xl,
                    ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = heroProgress.value
                            val s = (0.85f + 0.15f * heroProgress.value)
                            scaleX = s
                            scaleY = s
                        },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(spacing.xxxl)
                            .clip(CircleShape)
                            .background(Leaf600.copy(alpha = 0.25f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(sizing.emptyIcon),
                        )
                    }
                    Spacer(Modifier.size(spacing.md))
                    Text(
                        text = "Vedant Admin",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SurfaceWhite,
                    )
                    Text(
                        text = "Store operations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Leaf100,
                    )
                }
            }

            // ── Form fields ────────────────────────────────────────────
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
                modifier = Modifier.padding(spacing.xl),
            ) {
                Text(
                    text = "Sign in",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Vedant Fruit & Vegetables — store operations",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    singleLine = true,
                    isError = emailError != null,
                    supportingText = if (emailError != null) {
                        { Text(emailError) }
                    } else {
                        null
                    },
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = if (passwordError != null) {
                        { Text(passwordError) }
                    } else {
                        null
                    },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                imageVector = if (state.isPasswordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (state.isPasswordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                            )
                        }
                    },
                    visualTransformation = if (state.isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (submitError != null) {
                    Surface(
                        color = DangerBg,
                        contentColor = Danger,
                        shape = RoundedCornerShape(VedantTheme.radius.md),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = ErrorCopy.of(submitError),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(spacing.md),
                        )
                    }
                }

                PrimaryButton(
                    text = if (state.isSubmitting) "Signing in…" else "Sign in",
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    isInFlight = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Only store administrators can use this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
