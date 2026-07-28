package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpTransactionDao {
    @Query("SELECT * FROM http_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<HttpTransactionEntity>>

    @Query("SELECT * FROM http_transactions WHERE url LIKE '%' || :query || '%' OR method LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchTransactions(query: String): Flow<List<HttpTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(item: HttpTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(items: List<HttpTransactionEntity>)

    @Query("DELETE FROM http_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM http_transactions WHERE id IN (:ids)")
    suspend fun deleteTransactionsByIds(ids: List<Long>)

    @Query("DELETE FROM http_transactions")
    suspend fun clearAll()
}

@Dao
interface RepeaterDao {
    @Query("SELECT * FROM repeater_tabs ORDER BY id ASC")
    fun getAllTabs(): Flow<List<RepeaterTabEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: RepeaterTabEntity): Long

    @Update
    suspend fun updateTab(tab: RepeaterTabEntity)

    @Query("DELETE FROM repeater_tabs WHERE id = :id")
    suspend fun deleteTabById(id: Long)
}

@Dao
interface InterceptedRequestDao {
    @Query("SELECT * FROM intercepted_requests ORDER BY timestamp ASC")
    fun getAllIntercepted(): Flow<List<InterceptedRequestEntity>>

    @Query("SELECT * FROM intercepted_requests WHERE id = :id LIMIT 1")
    suspend fun getInterceptedById(id: Long): InterceptedRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntercepted(item: InterceptedRequestEntity): Long

    @Query("DELETE FROM intercepted_requests WHERE id = :id")
    suspend fun deleteIntercepted(id: Long)

    @Query("DELETE FROM intercepted_requests")
    suspend fun clearAll()
}

@Dao
interface TargetScopeDao {
    @Query("SELECT * FROM target_scopes ORDER BY id DESC")
    fun getAllScopes(): Flow<List<TargetScopeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScope(scope: TargetScopeEntity): Long

    @Update
    suspend fun updateScope(scope: TargetScopeEntity)

    @Query("DELETE FROM target_scopes WHERE id = :id")
    suspend fun deleteScope(id: Long)
}

@Dao
interface SecurityProjectDao {
    @Query("SELECT * FROM security_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<SecurityProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: SecurityProjectEntity): Long

    @Query("UPDATE security_projects SET isActive = (id = :activeId)")
    suspend fun setActiveProject(activeId: Long)

    @Query("DELETE FROM security_projects WHERE id = :id")
    suspend fun deleteProject(id: Long)
}
