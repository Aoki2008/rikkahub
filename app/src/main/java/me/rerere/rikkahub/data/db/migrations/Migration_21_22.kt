package me.rerere.rikkahub.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE ConversationEntity
            ADD COLUMN script_variables TEXT NOT NULL DEFAULT '{}'
            """.trimIndent()
        )
    }
}
