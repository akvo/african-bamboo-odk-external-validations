package com.akvo.externalodk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akvo.externalodk.ui.theme.ExternalODKTheme
import com.akvo.externalodk.ui.viewmodel.LoginUiState
import com.akvo.externalodk.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    onDownloadStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LoginScreenContent(
        uiState = uiState,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onServerUrlChange = viewModel::onServerUrlChange,
        onFormIdChange = viewModel::onFormIdChange,
        onDownloadClick = {
            viewModel.startLoginAndDownloadProcess()
            onDownloadStart()
        },
        modifier = modifier
    )
}

@Composable
private fun LoginScreenContent(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onFormIdChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = onServerUrlChange,
            label = { Text("Server URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.formId,
            onValueChange = onFormIdChange,
            label = { Text("Form ID / Asset URI") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDownloadClick,
            enabled = uiState.isFormValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download Data")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    ExternalODKTheme {
        LoginScreenContent(
            uiState = LoginUiState(),
            onUsernameChange = {},
            onPasswordChange = {},
            onServerUrlChange = {},
            onFormIdChange = {},
            onDownloadClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenFilledPreview() {
    ExternalODKTheme {
        LoginScreenContent(
            uiState = LoginUiState(
                username = "testuser",
                password = "password123",
                serverUrl = "https://eu.kobotoolbox.org",
                formId = "aXf2qPzKm8B7vN3cR9dE"
            ),
            onUsernameChange = {},
            onPasswordChange = {},
            onServerUrlChange = {},
            onFormIdChange = {},
            onDownloadClick = {}
        )
    }
}
