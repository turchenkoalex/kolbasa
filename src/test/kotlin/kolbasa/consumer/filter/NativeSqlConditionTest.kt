package kolbasa.consumer.filter

import io.mockk.confirmVerified
import io.mockk.mockk
import kolbasa.queue.PredefinedDataTypes
import kolbasa.queue.Queue
import kolbasa.utils.IntBox
import org.junit.jupiter.api.Test
import java.sql.PreparedStatement
import kotlin.test.assertEquals

class NativeSqlConditionTest {
    @Test
    fun testToSql() {
        val nativeExpression = NativeSqlCondition<TestMeta>(
            sqlPattern = "({0} like ''a%'') or ({0} like ''b%'') and {1} is null and {2} is null",
            fieldNames = listOf(TestMeta::stringValue.name, TestMeta::intValue.name, TestMeta::stringValue.name)
        )

        val sql = nativeExpression.toSqlClause(queue)
        assertEquals(
            expected = "(meta_string_value like 'a%') or (meta_string_value like 'b%') and meta_int_value is null and meta_string_value is null",
            actual = sql
        )
    }

    @Test
    fun testFillPreparedQuery() {
        val nativeExpression = NativeSqlCondition<TestMeta>(
            sqlPattern = "{0} is null",
            fieldNames = listOf(TestMeta::stringValue.name)
        )
        val preparedStatement = mockk<PreparedStatement>(relaxed = true)
        val column = IntBox(1)

        // call
        nativeExpression.toSqlClause(queue)
        nativeExpression.fillPreparedQuery(queue, preparedStatement, column)

        // check
        confirmVerified(preparedStatement)
    }

    companion object {
        data class TestMeta(val intValue: Int, val stringValue: String)

        val queue = Queue(
            "test_queue",
            dataType = PredefinedDataTypes.ByteArray,
            metadata = TestMeta::class.java
        )
    }
}
