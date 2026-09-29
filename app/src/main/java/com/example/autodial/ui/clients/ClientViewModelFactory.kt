package com.example.autodial.ui.clients

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.autodial.data.MainRepository

class ClientViewModelFactory(
    private val application: Application,
    private val repository: MainRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClientViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ClientViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
