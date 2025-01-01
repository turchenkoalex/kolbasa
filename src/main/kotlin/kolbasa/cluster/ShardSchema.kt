package kolbasa.cluster

import kolbasa.cluster.IdSchema.NODE_COLUMN_LENGTH
import kolbasa.pg.DatabaseExtensions.useConnectionWithAutocommit
import kolbasa.pg.DatabaseExtensions.useStatement
import kolbasa.schema.Const
import java.sql.Statement
import javax.sql.DataSource

internal object ShardSchema {

    // q__shard
    private const val SHARD_TABLE_NAME = Const.QUEUE_TABLE_NAME_PREFIX + "_shard"
    private const val SHARD_COLUMN_NAME = "shard"
    private const val PRODUCER_NODE_COLUMN_NAME = "producer_node"
    private const val CONSUMER_NODE_COLUMN_NAME = "consumer_node"
    private const val NEXT_CONSUMER_NODE_COLUMN_NAME = "next_consumer_node"

    private val CREATE_SHARD_TABLE_STATEMENT = """
        create table if not exists $SHARD_TABLE_NAME(
            $SHARD_COLUMN_NAME int not null primary key,
            $PRODUCER_NODE_COLUMN_NAME varchar($NODE_COLUMN_LENGTH) not null,
            $CONSUMER_NODE_COLUMN_NAME varchar($NODE_COLUMN_LENGTH),
            $NEXT_CONSUMER_NODE_COLUMN_NAME varchar($NODE_COLUMN_LENGTH),
            check (
                ($PRODUCER_NODE_COLUMN_NAME=$CONSUMER_NODE_COLUMN_NAME and $NEXT_CONSUMER_NODE_COLUMN_NAME is null) or
                ($PRODUCER_NODE_COLUMN_NAME=$NEXT_CONSUMER_NODE_COLUMN_NAME and $CONSUMER_NODE_COLUMN_NAME is null)
            )
        )
    """.trimIndent()

    private const val READ_SHARD_TABLE_STATEMENT = """
        select
            $SHARD_COLUMN_NAME, $PRODUCER_NODE_COLUMN_NAME, $CONSUMER_NODE_COLUMN_NAME, $NEXT_CONSUMER_NODE_COLUMN_NAME
        from
            $SHARD_TABLE_NAME
        order by
            $SHARD_COLUMN_NAME
    """

    fun createShardTable(dataSource: DataSource) {
        val ddlStatements = listOf(
            CREATE_SHARD_TABLE_STATEMENT,
        )

        dataSource.useConnectionWithAutocommit { connection ->
            // separate transaction for each statement
            connection.createStatement().use { statement ->
                ddlStatements.forEach { ddlStatement ->
                    statement.execute(ddlStatement)
                }
            }
        }
    }

    fun fillShardTable(dataSource: DataSource, nodes: List<String>) {
        val statements = (Shard.MIN_SHARD..Shard.MAX_SHARD).chunked(100).map { shards ->
            val values = shards.map { shard ->
                val randomNode = nodes.random()
                "($shard, '$randomNode', '$randomNode')"
            }

            """
                insert into $SHARD_TABLE_NAME
                    ($SHARD_COLUMN_NAME, $PRODUCER_NODE_COLUMN_NAME, $CONSUMER_NODE_COLUMN_NAME)
                values
                    ${values.joinToString(",")}
                on conflict do nothing
            """.trimIndent()
        }

        dataSource.useStatement { statement ->
            statements.forEach { statement.execute(it) }
        }
    }

    fun readShards(dataSource: DataSource): Map<Int, Shard> {
        val shards = mutableMapOf<Int, Shard>()

        dataSource.useStatement { statement: Statement ->
            statement.executeQuery(READ_SHARD_TABLE_STATEMENT).use { resultSet ->
                while (resultSet.next()) {
                    val shard = Shard(
                        shard = resultSet.getInt(SHARD_COLUMN_NAME),
                        producerNode = resultSet.getString(PRODUCER_NODE_COLUMN_NAME),
                        consumerNode = resultSet.getString(CONSUMER_NODE_COLUMN_NAME),
                        nextConsumerNode = resultSet.getString(NEXT_CONSUMER_NODE_COLUMN_NAME)
                    )
                    shards[shard.shard] = shard
                }
            }
        }

        return shards
    }

}
