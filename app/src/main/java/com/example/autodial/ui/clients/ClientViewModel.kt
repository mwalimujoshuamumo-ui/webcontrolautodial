package com.example.autodial.ui.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autodial.data.*
import com.example.autodial.util.ClientManager
import com.example.autodial.util.ContactsHelper
import com.example.autodial.util.PreferenceManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClientViewModel(application: Application, private val repository: MainRepository) : AndroidViewModel(application) {

    private val prefManager = PreferenceManager(application)
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _categoryFilter = MutableStateFlow("All")
    val categoryFilter: StateFlow<String> = _categoryFilter

    val allClients = combine(_searchQuery.debounce(300), _categoryFilter) { query, category ->
        Pair(query, category)
    }.flatMapLatest { (query, category) ->
        when (category) {
            "Most Active" -> repository.clientDao.getClientsByActivity()
            "Most Spent" -> repository.clientDao.getClientsBySpending()
            "Most Recent" -> repository.clientDao.getClientsByRecency()
            "Debtors" -> repository.clientDao.getAllClients().map { list -> list.filter { it.outstandingDebt > 0 } }
            "Blacklisted" -> repository.clientDao.getAllClients().map { list -> list.filter { it.isBlacklisted } }
            else -> if (query.isEmpty()) repository.clientDao.getAllClients()
                    else repository.clientDao.searchClients(query)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val clientCount = repository.clientDao.getClientCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val blacklistedCount = repository.clientDao.getBlacklistedCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)
    val debtorsCount = repository.clientDao.getDebtorsCount().stateIn(viewModelScope, SharingStarted.Lazily, 0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _categoryFilter.value = category
    }

    fun deleteClients(ids: List<Int>) {
        viewModelScope.launch {
            repository.clientDao.deleteClients(ids)
        }
    }

    fun addClient(name: String, phone: String, initialDebt: Double) {
        viewModelScope.launch {
            val existing = repository.clientDao.getClientByPhone(phone)
            if (existing == null) {
                val clientId = repository.clientDao.insertClient(Client(name = name, phoneNumber = phone))
                if (initialDebt > 0) {
                    ClientManager.recordDebt(getApplication(), clientId.toInt(), initialDebt, "Initial debt added manually")
                }
                
                if (prefManager.isAutosaveContactsEnabled()) {
                    val prefix = prefManager.getAutosavePrefix()
                    ContactsHelper.saveContact(getApplication(), name, phone, prefix)
                }
            } else {
                // Update existing if name was empty
                if (existing.name.isBlank() || existing.name == "UNKNOWN") {
                    repository.clientDao.updateClient(existing.copy(name = name))
                }
                if (initialDebt > 0) {
                    ClientManager.recordDebt(getApplication(), existing.id, initialDebt, "Additional debt added manually")
                }
                
                if (prefManager.isAutosaveContactsEnabled()) {
                    val prefix = prefManager.getAutosavePrefix()
                    ContactsHelper.saveContact(getApplication(), name, phone, prefix)
                }
            }
        }
    }

    fun toggleBlacklist(client: Client) {
        viewModelScope.launch {
            val updated = if (client.isBlacklisted) {
                client.copy(isBlacklisted = false, unblacklistedAt = System.currentTimeMillis())
            } else {
                client.copy(isBlacklisted = true, blacklistedAt = System.currentTimeMillis())
            }
            repository.clientDao.updateClient(updated)
        }
    }

    fun addDebt(clientId: Int, amount: Double, notes: String) {
        viewModelScope.launch {
            ClientManager.recordDebt(getApplication(), clientId, amount, notes)
        }
    }

    fun recordPayment(clientId: Int, amount: Double, notes: String) {
        viewModelScope.launch {
            ClientManager.recordPayment(getApplication(), clientId, amount, notes)
        }
    }

    fun broadcastSms(clientIds: List<Int>, message: String, notes: String = "") {
        viewModelScope.launch {
            ClientManager.sendBroadcastSms(getApplication(), clientIds, message, notes)
        }
    }

    fun getClient(clientId: Int): Flow<Client?> = flow {
        emit(repository.clientDao.getClientById(clientId))
    }

    fun getDebtHistory(clientId: Int): Flow<List<DebtRecord>> = repository.clientDao.getDebtRecordsForClient(clientId)

    fun getSmsHistory(clientId: Int): Flow<List<ClientSms>> = repository.clientDao.getSmsHistoryForClient(clientId)
    
    fun getRecentTransactions(phone: String): Flow<List<Transaction>> = repository.allTransactions.map { list ->
        list.filter { it.customerNumber == phone }.take(5)
    }
}
