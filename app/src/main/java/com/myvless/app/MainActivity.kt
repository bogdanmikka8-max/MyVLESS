package com.bogdanmikka.myvless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bogdanmikka.myvless.ui.theme.MyVLESSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyVLESSTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var isConnected by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Готов к подключению") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MyVLESS — Telegram Proxy") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    isConnected = !isConnected
                    status = if (isConnected) "Подключено (Telegram)" else "Отключено"
                    // TODO: Запуск сервиса
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isConnected) "Отключить" else "Подключить для Telegram")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Просто добавь VLESS конфиг и нажми кнопку выше.\nTelegram будет работать через прокси.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
