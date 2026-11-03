package at.techbee.gplaypromocodeprovider.admin.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.components.ErrorText
import at.techbee.gplaypromocodeprovider.ui.NETWORK_ERROR_TEXT
import at.techbee.gplaypromocodeprovider.ui.SERVER_ERROR_TEXT

/** The admin login form. */
@Composable
fun LoginScreen(state: AdminSessionState.LoggedOut, onLogin: (username: String, password: String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val canLogin = !state.loggingIn && username.isNotBlank() && password.isNotEmpty()
    val submit = { if (canLogin) onLogin(username, password) }

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Admin login", style = MaterialTheme.typography.headlineMedium)
            if (state.sessionExpired) {
                Text("Your session has ended. Please log in again.", style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                enabled = !state.loggingIn,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                enabled = !state.loggingIn,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let {
                ErrorText(
                    when (it) {
                        LoginError.WRONG_CREDENTIALS -> "Wrong username or password."
                        LoginError.NETWORK -> NETWORK_ERROR_TEXT
                        LoginError.SERVER -> SERVER_ERROR_TEXT
                    },
                )
            }
            Button(onClick = submit, enabled = canLogin, modifier = Modifier.align(Alignment.End)) {
                Text(if (state.loggingIn) "Logging in…" else "Log in")
            }
        }
    }
}

@Composable
private fun LoginPreview(state: AdminSessionState.LoggedOut) {
    PreviewTheme { LoginScreen(state, onLogin = { _, _ -> }) }
}

@Preview(widthDp = 400, heightDp = 400)
@Composable
private fun LoginScreenPreview() = LoginPreview(AdminSessionState.LoggedOut())

@Preview(widthDp = 400, heightDp = 450)
@Composable
private fun LoginWrongCredentialsPreview() = LoginPreview(AdminSessionState.LoggedOut(error = LoginError.WRONG_CREDENTIALS))

@Preview(widthDp = 400, heightDp = 450)
@Composable
private fun LoginSessionExpiredPreview() = LoginPreview(AdminSessionState.LoggedOut(sessionExpired = true))

@Preview(widthDp = 400, heightDp = 400)
@Composable
private fun LoginInProgressPreview() = LoginPreview(AdminSessionState.LoggedOut(loggingIn = true))
