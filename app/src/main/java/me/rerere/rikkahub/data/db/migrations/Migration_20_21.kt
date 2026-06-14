package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE ConversationEntity
            ADD COLUMN group_id TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}
